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
    private Diagram diagram = new Diagram();

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }
    public Chat getChat() { return chat; }
    public void setChat(Chat chat) { this.chat = chat; }
    public Crypto getCrypto() { return crypto; }
    public void setCrypto(Crypto crypto) { this.crypto = crypto; }
    public Startup getStartup() { return startup; }
    public void setStartup(Startup startup) { this.startup = startup; }
    public Diagram getDiagram() { return diagram; }
    public void setDiagram(Diagram diagram) { this.diagram = diagram; }

    public static class Storage {
        private String imageDir = "./data/images";
        public String getImageDir() { return imageDir; }
        public void setImageDir(String imageDir) { this.imageDir = imageDir; }
    }

    public static class Chat {
        /** CR-049 / D-15：与 application.yaml 保持一致，单测直接 new AppProperties() 时也应是 180s。 */
        private long sseTimeoutMs = 180000;
        private long hardTimeoutMs = 180000;
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

    /**
     * CR-047：Mermaid → PNG 的后端渲染参数。浏览器路径留空时按 Chrome / Edge 的常见安装位置探测；
     * 工作目录存放 mermaid.min.js 与临时渲染页（每次渲染后清理临时文件）。
     */
    public static class Diagram {
        private String chromePath = "";
        private String workDir = "./data/diagram";
        private long timeoutMs = 15000;
        private int maxCodeChars = 4000;
        private int windowWidth = 1400;
        private int windowHeight = 1100;
        private int scale = 2;
        private long virtualTimeBudgetMs = 8000;

        public String getChromePath() { return chromePath; }
        public void setChromePath(String chromePath) { this.chromePath = chromePath; }
        public String getWorkDir() { return workDir; }
        public void setWorkDir(String workDir) { this.workDir = workDir; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getMaxCodeChars() { return maxCodeChars; }
        public void setMaxCodeChars(int maxCodeChars) { this.maxCodeChars = maxCodeChars; }
        public int getWindowWidth() { return windowWidth; }
        public void setWindowWidth(int windowWidth) { this.windowWidth = windowWidth; }
        public int getWindowHeight() { return windowHeight; }
        public void setWindowHeight(int windowHeight) { this.windowHeight = windowHeight; }
        public int getScale() { return scale; }
        public void setScale(int scale) { this.scale = scale; }
        public long getVirtualTimeBudgetMs() { return virtualTimeBudgetMs; }
        public void setVirtualTimeBudgetMs(long virtualTimeBudgetMs) { this.virtualTimeBudgetMs = virtualTimeBudgetMs; }
    }
}
