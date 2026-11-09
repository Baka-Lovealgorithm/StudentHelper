package com.ai.studenthelper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用配置绑定（技术规范 8.1）。环境相关值全部来自环境变量占位符。
 */
@ConfigurationProperties(prefix = "studenthelper")
public class AppProperties {

    private Storage storage = new Storage();
    private Chat chat = new Chat();
    private Crypto crypto = new Crypto();
    private Startup startup = new Startup();

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }
    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat; }
    public Crypto getCrypto() { return crypto; }
    public void setCrypto(Crypto crypto) { this.crypto = crypto; }
    public Startup getStartup() { return startup; }
    public void setStartup(Startup startup) { this.startup = startup; }

    public static class Storage {
        private String imageDir = "./data/images";
        public String getImageDir() { return imageDir; }
        public void setImageDir(String imageDir) { this.imageDir = imageDir; }
    }

    public static class Chat {
        private long sseTimeoutMs = 60000;
        private long hardTimeoutMs = 45000;
        private int maxImagesPerMessage = 4;
        public long getSseTimeoutMs() { return sseTimeoutMs; }
        public void setSseTimeoutMs(long sseTimeoutMs) { this.sseTimeoutMs = sseTimeoutMs; }
        public long getHardTimeoutMs() { return hardTimeoutMs; }
        public void setHardTimeoutMs(long hardTimeoutMs) { this.hardTimeoutMs = hardTimeoutMs; }
        public int getMaxImagesPerMessage() { return maxImagesPerMessage; }
        public void setMaxImagesPerMessage(int maxImagesPerMessage) { this.maxImagesPerMessage = maxImagesPerMessage; }
    }

    public static class Crypto {
        private String masterKey = "";
        private String masterKeyFile = "./data/.master_key";
        public String getMasterKey() { return masterKey; }
        public void setMasterKey(String masterKey) { this.masterKey = masterKey; }
        public String getMasterKeyFile() { return masterKeyFile; }
        public void setMasterKeyFile(String masterKeyFile) { this.masterKeyFile = masterKeyFile; }
    }

    public static class Startup {
        private boolean dbCheck = true;
        public boolean isDbCheck() { return dbCheck; }
        public void setDbCheck(boolean dbCheck) { this.dbCheck = dbCheck; }
    }
}
