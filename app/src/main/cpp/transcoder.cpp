#include <jni.h>
#include <cstdio>
#include <cstdarg>
#include <cstdlib>
#include <cstring>
#include <cinttypes>
#include <android/log.h>

#ifdef __cplusplus
extern "C" {
#endif
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavutil/audio_fifo.h"
#include "libavutil/timestamp.h"
#include "libavutil/opt.h"
#include "libswresample/swresample.h"
#ifdef __cplusplus
}
#endif

// #define DEBUG
#ifdef DEBUG
#define ALOG(prio, tag, text) \
    do { __android_log_write(prio, tag, text); } while (0)
#else
#define ALOG(prio, tag, text) \
    do {  } while (0)
#endif


static const char   TAG[] = "NTranscoder";
static char         logprint_buf[65536];
static int64_t      pts = 0;

static const char * to_log(
        const char * fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vsnprintf(logprint_buf, 65536, fmt, args);
    va_end(args);
    return logprint_buf;
}

static const char * get_file_abs_path(
        JNIEnv *    env,
        jobject     file_obj) {

    jclass cls_File                 = env->GetObjectClass(file_obj);
    jmethodID mid_get_absolute_path = env->GetMethodID(cls_File,
                                                       "getAbsolutePath",
                                                       "()Ljava/lang/String;");
    jstring jstr_path               = (jstring) env->CallObjectMethod(file_obj, mid_get_absolute_path);
    const char * path               = env->GetStringUTFChars(jstr_path, NULL);

    return path;

}

static int open_input_file(
        const char *        path,
        AVFormatContext **  input_format_ctx,
        AVCodecContext  **  input_codec_ctx) {

    int av_ret = 0;

    if ((av_ret = avformat_open_input(input_format_ctx, path, NULL, NULL)) != 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to open input file @ avformat_open_input() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        return av_ret;
    }

    if ((av_ret = avformat_find_stream_info(*input_format_ctx, NULL)) < 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to open input file @ avformat_find_stream_info() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        avformat_close_input(input_format_ctx);
        return av_ret;
    }

    ALOG(ANDROID_LOG_VERBOSE, TAG,
                        to_log("Input file has nb_streams: %d", (*input_format_ctx)->nb_streams));

    bool is_stream_selected = false;

    for (int i = 0; i < (*input_format_ctx)->nb_streams; i++) {
        if ((*input_format_ctx)->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            ALOG(ANDROID_LOG_VERBOSE, TAG,
                                to_log("Selecting stream %d from input file.", (*input_format_ctx)->nb_streams));
            AVStream * avstream     = (*input_format_ctx)->streams[i];
            const AVCodec * avcodec = avcodec_find_decoder((*input_format_ctx)->streams[i]->codecpar->codec_id);
            if (!avcodec) {
                ALOG(ANDROID_LOG_ERROR, TAG,
                                    to_log("Failed to open input file @ avcodec_find_decoder(): no codec found?"));
                avformat_close_input(input_format_ctx);
                return AVERROR_DECODER_NOT_FOUND;
            }

            *input_codec_ctx = avcodec_alloc_context3(avcodec);
            if (!(*input_codec_ctx)) {
                ALOG(ANDROID_LOG_ERROR, TAG,
                                    to_log("Failed to open input file @ avcodec_alloc_context3() -> %s",
                                           av_make_error_string((char[64]) {0}, 64, av_ret)));
                avformat_close_input(input_format_ctx);
                return AVERROR(ENOMEM);
            }
            if ((av_ret = avcodec_parameters_to_context(*input_codec_ctx, (*input_format_ctx)->streams[i]->codecpar)) < 0) {
                ALOG(ANDROID_LOG_ERROR, TAG,
                                    to_log("Failed to open input file @ avcodec_parameters_to_context() -> %s",
                                           av_make_error_string((char[64]) {0}, 64, av_ret)));
                avformat_close_input(input_format_ctx);
                avcodec_free_context(input_codec_ctx);
                return av_ret;
            }
            if ((av_ret = avcodec_open2(*input_codec_ctx, avcodec, NULL)) < 0) {
                ALOG(ANDROID_LOG_ERROR, TAG,
                                    to_log("Failed to open input file @ avcodec_open2() -> %s",
                                           av_make_error_string((char[64]) {0}, 64, av_ret)));
                avformat_close_input(input_format_ctx);
                avcodec_free_context(input_codec_ctx);
                return av_ret;
            }

            (*input_codec_ctx)->pkt_timebase = avstream->time_base;
            is_stream_selected = true;
            break; // we are expecting only 1 AVMEDIA_TYPE_AUDIO stream
        }
    }

    if (!is_stream_selected) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("No stream selected from input"));
        return AVERROR_UNKNOWN;
    }

    return av_ret;
}

static int open_output_file(
        const char *        path,
        AVCodecContext *    input_codec_ctx,
        AVFormatContext **  output_format_ctx,
        AVCodecContext **   output_codec_ctx) {

    int av_ret              = 0;
    AVIOContext * avio_ctx  = NULL;
    const AVCodec * avcodec = NULL;
    AVStream * avstream     = NULL;

    if ((av_ret = avio_open(&avio_ctx, path, AVIO_FLAG_WRITE)) < 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to open output file @ avio_open() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        return av_ret;
    }

    if (!(*output_format_ctx = avformat_alloc_context())) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to open output file @ avformat_alloc_context()"));
        return AVERROR(ENOMEM);
    }

    (*output_format_ctx)->pb = avio_ctx;

    // if guessing fails, we can explicitly mention MP3.
    if (!((*output_format_ctx)->oformat = av_guess_format(NULL, path, NULL))) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to open output file @ av_guess_format(): couldn't guess?"));
        av_ret = AVERROR_EXIT;
        goto cleanup_avio_fc;
    }

    if (!((*output_format_ctx)->url = av_strdup(path))) {
        av_ret = AVERROR(ENOMEM);
        goto cleanup_avio_fc;
    }

    if (!(avcodec = avcodec_find_encoder(AV_CODEC_ID_MP3))) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to open output file @ avcodec_find_encoder(): no MP3 encoder?"));
        av_ret = AVERROR_ENCODER_NOT_FOUND;
        goto cleanup_avio_fc;
    }

    if (!(avstream = avformat_new_stream(*output_format_ctx, NULL))) {
        to_log("Failed to open output file @ avcodec_alloc_context3(): ENOMEM?");
        av_ret = AVERROR(ENOMEM);
        goto cleanup_avio_fc;
    }

    if (!(*output_codec_ctx = avcodec_alloc_context3(avcodec))) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to open output file @ avcodec_alloc_context3(): ENOMEM?"));
        av_ret = AVERROR(ENOMEM);
        goto cleanup_avio_fc;
    }

    av_channel_layout_default(&(*output_codec_ctx)->ch_layout, 2);
    (*output_codec_ctx)->sample_rate    = input_codec_ctx->sample_rate;
    (*output_codec_ctx)->sample_fmt     = avcodec->sample_fmts[0];
    (*output_codec_ctx)->bit_rate       = 96000;

    avstream->time_base.den             = input_codec_ctx->sample_rate;
    avstream->time_base.num             = 1;

    if ((*output_format_ctx)->oformat->flags & AVFMT_GLOBALHEADER) {
        (*output_codec_ctx)->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }

    if ((av_ret = avcodec_open2(*output_codec_ctx, avcodec, NULL))) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to open output file @ avcodec_open2() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        goto cleanup_cc;
    }

    if ((av_ret = avcodec_parameters_from_context(avstream->codecpar, *output_codec_ctx)) < 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to open output file @ avcodec_parameters_from_context() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        goto cleanup_cc;
    }

    return av_ret;

cleanup_cc:
    avcodec_free_context(output_codec_ctx);
cleanup_avio_fc:
    avio_closep(&avio_ctx);
    avformat_free_context(*output_format_ctx);
    *output_format_ctx = NULL;
    return av_ret;

}

static int init_resampler(
        AVCodecContext *    input_codec_ctx,
        AVCodecContext *    output_codec_ctx,
        SwrContext **       resample_ctx) {

    int av_ret = 0;

    if ((av_ret = swr_alloc_set_opts2(
            resample_ctx,
            &output_codec_ctx->ch_layout, output_codec_ctx->sample_fmt, output_codec_ctx->sample_rate,
            &input_codec_ctx->ch_layout, input_codec_ctx->sample_fmt, input_codec_ctx->sample_rate,
            0, NULL))) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to init audio resampler @ init_resampler() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        return av_ret;
    }

    if (output_codec_ctx->sample_rate != input_codec_ctx->sample_rate) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to init audio resampler: sample_rate mismatch!"));
        swr_free(resample_ctx);
        return AVERROR_EXIT;
    }

    if ((av_ret = swr_init(*resample_ctx)) < 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to init audio resampler @ swr_init() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        swr_free(resample_ctx);
        return av_ret;
    }

    return av_ret;
}

static int init_fifo(
        AVAudioFifo **      fifo,
        AVCodecContext *    output_codec_ctx) {

    if (!(*fifo = av_audio_fifo_alloc(
            output_codec_ctx->sample_fmt,
            output_codec_ctx->ch_layout.nb_channels, 1))) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to init audio fifo @ av_audio_fifo_alloc: ENOMEM?"));
        return AVERROR(ENOMEM);
    }

    return 0;
}

static int write_output_header(
        AVFormatContext * output_format_ctx) {

    int av_ret = 0;

    if ((av_ret = avformat_write_header(output_format_ctx, NULL)) < 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to write output header @ avformat_write_header() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
    }

    return av_ret;

}

static int init_input_frame(
        AVFrame ** avframe) {

    if (!(*avframe = av_frame_alloc())) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to init AVFrame @ av_frame_alloc: ENOMEM?"));
        return AVERROR(ENOMEM);
    }

    return 0;

}

static int init_packet(
        AVPacket ** packet) {

    if (!(*packet = av_packet_alloc())) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to init AVPacket @ av_packet_alloc: ENOMEM?"));
        return AVERROR(ENOMEM);
    }

    return 0;

}

static int decode_audio_frame(
        AVFrame *           frame,
        AVFormatContext *   input_format_context,
        AVCodecContext *    input_codec_context,
        int *               data_present,
        int *               finished) {

    int av_ret = 0;
    AVPacket * input_packet = NULL;

    if ((av_ret = init_packet(&input_packet)) != 0) {
        return av_ret;
    }

    *data_present = 0;
    *finished = 0;

    if ((av_ret = av_read_frame(input_format_context, input_packet)) < 0) {
        if (av_ret == AVERROR_EOF) {
            *finished = 1;
        } else {
            ALOG(ANDROID_LOG_ERROR, TAG,
                                to_log("Failed to decode audio frame @ av_read_frame() -> %s",
                                       av_make_error_string((char[64]) {0}, 64, av_ret)));
            goto free_avpacket;
        }
    }

    if ((av_ret = avcodec_send_packet(input_codec_context, input_packet)) < 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to decode audio frame @ av_read_frame() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        goto free_avpacket;
    }

    av_ret = avcodec_receive_frame(input_codec_context, frame);

    if (av_ret == AVERROR(EAGAIN)) {
        av_ret = 0;
    } else if (av_ret == AVERROR_EOF) {
        *finished = 1;
        av_ret = 0;
    } else if (av_ret < 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to decode audio frame @ avcodec_receive_frame() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
    } else {
        // av_ret will be 0 since a frame is successfully returned
        *data_present = 1;
    }

free_avpacket:
    av_packet_free(&input_packet);
    return av_ret;

}

static int init_converted_samples(
        uint8_t ***         converted_input_samples,
        AVCodecContext *    output_codec_ctx,
        int                 frame_size) {

    int av_ret = 0;

    if ((av_ret = av_samples_alloc_array_and_samples(converted_input_samples, NULL,
                                                     output_codec_ctx->ch_layout.nb_channels, frame_size,
                                                     output_codec_ctx->sample_fmt, 0)) < 0) {

        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to allocate converted input samples @ av_samples_alloc_array_and_samples() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
    }

    return 0;

}

static int convert_samples(
        const uint8_t **    input_data,
        uint8_t **          converted_data,
        const int           frame_size,
        SwrContext *        resample_ctx) {

    int av_ret = 0;

    if ((av_ret = swr_convert(resample_ctx,
                              converted_data, frame_size,
                              input_data, frame_size)) < 0) {

        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to converted input samples @ swr_convert() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
    }

    return 0;

}

static int add_samples_to_fifo(
        AVAudioFifo *   fifo,
        uint8_t **      converted_input_samples,
        const int       frame_size) {

    int av_ret = 0;

    if ((av_ret = av_audio_fifo_realloc(fifo, av_audio_fifo_size(fifo) + frame_size)) < 0) {

        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to add samples to fifo @ av_audio_fifo_realloc() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
    }

    if (av_audio_fifo_write(fifo, (void **) converted_input_samples, frame_size) < frame_size) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed write to fifo @ av_audio_fifo_write"));
        return AVERROR_EXIT;
    }

    return av_ret;

}

static int read_decode_convert_and_store(
        AVAudioFifo *       fifo,
        AVFormatContext *   input_format_ctx,
        AVCodecContext *    input_codec_ctx,
        AVCodecContext *    output_codec_ctx,
        SwrContext *        resampler_ctx,
        int *               finished) {

    int result = 0;

    AVFrame *   input_frame             = NULL;
    uint8_t **  converted_input_samples = NULL;
    int         data_present;

    if ((result = init_input_frame(&input_frame)) != 0) {
        return result;
    }

    if (decode_audio_frame(input_frame, input_format_ctx, input_codec_ctx,
                           &data_present, finished) != 0) {
        goto free_frame;
    }

    if (*finished) {
        result = 0;
        goto free_frame;
    }

    if (data_present) {
        if (init_converted_samples(&converted_input_samples, output_codec_ctx, input_frame->nb_samples) != 0) {
            goto free_frame;
        }

        if (convert_samples((const uint8_t **)input_frame->extended_data, 
                            converted_input_samples, input_frame->nb_samples, resampler_ctx) != 0) {
            goto free_input_samples;
        }

        if (add_samples_to_fifo(fifo, converted_input_samples, input_frame->nb_samples) != 0) {
            goto free_input_samples;
        }
    }

free_input_samples:
    if (converted_input_samples) av_freep(&converted_input_samples[0]);
    av_freep(&converted_input_samples);
free_frame:
    av_frame_free(&input_frame);
    return result;

}

static int init_output_frame(
        AVFrame **          frame,
        AVCodecContext *    output_codec_context,
        int                 frame_size) {

    int av_ret = 0;

    if (!(*frame = av_frame_alloc())) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed init output frame @ av_frame_alloc: ENOMEM?"));
        return AVERROR(ENOMEM);
    }

    (*frame)->nb_samples    = frame_size;
    (*frame)->format        = output_codec_context->sample_fmt;
    (*frame)->sample_rate   = output_codec_context->sample_rate;
    av_channel_layout_copy(&(*frame)->ch_layout, &output_codec_context->ch_layout);

    if ((av_ret = av_frame_get_buffer(*frame, 0)) < 0) {

        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to init output frame @ av_frame_get_buffer() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        av_frame_free(frame);
    }

    return av_ret;

}

static int encode_audio_frame(
        AVFrame *           frame,
        AVFormatContext *   output_format_context,
        AVCodecContext *    output_codec_context,
        int *               data_present) {

    int av_ret = 0;
    AVPacket * output_packet;

    if ((av_ret = init_packet(&output_packet)) < 0) {
        return av_ret;
    }

    if (frame) {
        frame->pts = pts;
        pts += frame->nb_samples;
    }

    *data_present = 0;

    if ((av_ret = avcodec_send_frame(output_codec_context, frame)) < 0 && av_ret != AVERROR_EOF) {

        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to encode audio frame @ avcodec_send_frame() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        goto free_packet;
    }

    av_ret = avcodec_receive_packet(output_codec_context, output_packet);

    if (av_ret == AVERROR(EAGAIN)) {
        av_ret = 0;
        goto free_packet;
    } else if (av_ret == AVERROR_EOF) {
        av_ret = 0;
        goto free_packet;
    } else if (av_ret < 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to encode audio frame @ avcodec_receive_packet() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        goto free_packet;
    } else {
        // av_ret will be 0 since avcodec_receive_packet() was successful.
        *data_present = 1;
    }

    if (*data_present && (av_ret = av_write_frame(output_format_context, output_packet)) < 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to encode audio frame @ av_write_frame() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
        goto free_packet;
    }

free_packet:
    av_packet_free(&output_packet);
    return av_ret;

}

static int load_encode_and_write(
        AVAudioFifo *       fifo,
        AVFormatContext *   output_format_context,
        AVCodecContext *    output_codec_context) {

    AVFrame * output_frame;
    const int frame_size = FFMIN(av_audio_fifo_size(fifo), output_codec_context->frame_size);
    int data_written;

    if (init_output_frame(&output_frame, output_codec_context, frame_size) != 0) {
        return AVERROR_EXIT;
    }

    if (av_audio_fifo_read(fifo, (void **) output_frame->data, frame_size) < frame_size) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed read from fifo @ av_audio_fifo_read"));
        av_frame_free(&output_frame);
        return AVERROR_EXIT;
    }

    if (encode_audio_frame(output_frame, output_format_context, output_codec_context, &data_written) != 0) {
        av_frame_free(&output_frame);
        return AVERROR_EXIT;
    }

    av_frame_free(&output_frame);
    return 0;
}

static int write_output_file_trailer(
        AVFormatContext * output_format_context) {

    int av_ret;

    if ((av_ret = av_write_trailer(output_format_context)) < 0) {
        ALOG(ANDROID_LOG_ERROR, TAG,
                            to_log("Failed to write output trailer @ av_write_trailer() -> %s",
                                   av_make_error_string((char[64]) {0}, 64, av_ret)));
    }

    return av_ret;

}

// With reference to FFmpeg's transcode_aac example code.
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_notesrecorder2_Transcoder_convert3gpToMp3(JNIEnv *env, jclass clazz,
                                                           jobject input, jobject output) {

    int                 result              = JNI_FALSE;

    const char *        input_path          = get_file_abs_path(env, input);
    const char *        output_path         = get_file_abs_path(env, output);

    AVFormatContext *   input_format_ctx    = NULL;
    AVCodecContext *    input_codec_ctx     = NULL;
    AVFormatContext *   output_format_ctx   = NULL;
    AVCodecContext *    output_codec_ctx    = NULL;
    SwrContext *        resample_ctx        = NULL;
    AVAudioFifo *       fifo                = NULL;

    pts                                     = 0; // reset global timestamp for audio frames

    if (open_input_file(input_path, &input_format_ctx, &input_codec_ctx) != 0) {
        return result;
    }

    if (open_output_file(output_path, input_codec_ctx, &output_format_ctx, &output_codec_ctx) != 0) {
        goto free_av_input;
    }

    if (init_resampler(input_codec_ctx, output_codec_ctx, &resample_ctx) != 0) {
        goto free_av_output;
    }

    if (init_fifo(&fifo, output_codec_ctx) != 0) {
        goto free_resampler;
    }

    if (write_output_header(output_format_ctx) != 0) {
        goto free_fifo;
    }

    // TODO: This loop can run into issues for big files...
    while (1) {
        const int output_frame_size = output_codec_ctx->frame_size;
        int finished                = 0;
        int av_fifo_size            = 0;

        // decode until we have enough to encode
        while ((av_fifo_size = av_audio_fifo_size(fifo)) < output_frame_size) {
            ALOG(ANDROID_LOG_DEBUG, TAG,
                                to_log("Decoding: av_fifo_size -> %d, output_frame_size -> %d",
                                       av_fifo_size, output_frame_size));
            if (read_decode_convert_and_store(fifo, input_format_ctx, input_codec_ctx,
                                              output_codec_ctx, resample_ctx, &finished) != 0) {
                goto free_fifo;
            }

            if (finished) break;
        }

        // encode
        while ((av_fifo_size = av_audio_fifo_size(fifo)) >= output_frame_size ||
                (finished && av_audio_fifo_size(fifo) > 0)) {
            ALOG(ANDROID_LOG_DEBUG, TAG,
                                to_log("Encoding: av_fifo_size -> %d, output_frame_size -> %d, finished -> %d",
                                       av_fifo_size, output_frame_size, finished));
            if (load_encode_and_write(fifo, output_format_ctx, output_codec_ctx) != 0) {
                goto free_fifo;
            }
        }

        // once all decode -> encode is complete, write to file.
        if (finished) {
            int data_written;

            do {
                if (encode_audio_frame(
                        NULL, output_format_ctx, output_codec_ctx, &data_written) != 0) {
                    ALOG(ANDROID_LOG_DEBUG, TAG,
                                        to_log("Encoding: failed @ encode_audio_frame() -> != 0"));
                    goto free_fifo;
                }
            } while (data_written);
            break;
        }
    }

    if (write_output_file_trailer(output_format_ctx) != 0) {
        goto free_fifo;
    }

    result = JNI_TRUE;
    ALOG(ANDROID_LOG_VERBOSE, TAG, "Transcoding completed successfully.");

free_fifo:
    if (fifo)               { av_audio_fifo_free(fifo); fifo = NULL; }
free_resampler:
    if (resample_ctx)       { swr_free(&resample_ctx); }
free_av_output:
    if (output_codec_ctx)   { avcodec_free_context(&output_codec_ctx); }
    if (output_format_ctx)  { avformat_free_context(output_format_ctx); output_format_ctx = NULL; }
free_av_input:
    if (input_codec_ctx)    { avcodec_free_context(&input_codec_ctx); }
    if (input_format_ctx)   { avformat_free_context(input_format_ctx); input_format_ctx = NULL; }
exit:
    return result;

}