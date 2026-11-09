package com.ai.studenthelper.common;

/**
 * 上传图片的类型判定：只认文件头签名，不信任扩展名与客户端声明的 Content-Type
 * （AC-101-3、NFR-108）。由 Controller 在落盘与调用模型之前调用。
 */
public final class ImageFileValidator {

    private ImageFileValidator() {
    }

    /** 返回受支持的 MIME；无法确认、被截断或伪装的文件一律返回 null。 */
    public static String detectMime(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        // PNG：完整 8 字节签名 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        // JPEG：FF D8 FF
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // WEBP：RIFF ???? WEBP
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /** MIME → 落盘扩展名；只允许受支持的类型，杜绝把外部字符串拼进文件名。 */
    public static String extensionFor(String mime) {
        return switch (mime) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("不支持的图片类型");
        };
    }
}
