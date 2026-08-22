package com.frameflow.learning.product.service;

/** 视频容器格式的魔数（magic bytes）校验。 */
public final class MediaSignature {

    private MediaSignature() {
    }

    /**
     * 检查文件头字节是否像视频容器。
     *
     * @return null = 通过；否则返回"为什么不像"的描述（写入 probe_error 留痕）。
     * F3 只做入口级校验（挡住"根本不是视频"的文件）；深度媒体探针
     * （时长/分辨率/帧率/可解码性）是 F4 Python worker 的工作——
     * Java 侧引入 ffmpeg 进程会破坏部署边界。
     */
    public static String check(byte[] head) {
        if (head.length >= 12 && head[4] == 'f' && head[5] == 't'
                && head[6] == 'y' && head[7] == 'p') {
            return null;   // MP4 / MOV / M4V 家族：第 4-7 字节是 "ftyp"
        }
        if (head.length >= 4 && (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45
                && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3) {
            return null;   // WebM / MKV：EBML 头
        }
        return "媒体签名不匹配：文件头不是已支持的视频容器"
                + "（MP4/MOV 的 ftyp 或 WebM/MKV 的 EBML），实际头部=" + toHex(head, 8);
    }

    private static String toHex(byte[] bytes, int limit) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, limit); i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }
}
