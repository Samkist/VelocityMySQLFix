package net.lumae.velocityfix;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.lumae.velocityfix.task.FileDownloadTask;
import net.lumae.velocityfix.util.ReflectUtil;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class VelocityMySQLFix {
    private static final String MYSQL_VERSION = "8.0.33";
    private static final String MYSQL_SHA256 = "e2a3b2fc726a1ac64e998585db86b30fa8bf3f706195b78bb77c5f99bf877bd9";
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public VelocityMySQLFix(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) throws Exception {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        if (ReflectUtil.getClass("com.mysql.cj.jdbc.Driver") != null) {
            logger.info("MySQL has already been implemented on this server. Exiting.");
            return;
        }
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }
        if (loadCache()) {
            return;
        }
        downloadAndLoad();
    }

    private String getMySQLLibraryName() {
        return "mysql-connector-j-" + MYSQL_VERSION + ".jar";
    }

    private File getMySQLLibraryFile() {
        return new File(dataDirectory.toFile(), getMySQLLibraryName());
    }

    private String getMySQLDownloadUrl() {
        return "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/"
                .concat(MYSQL_VERSION)
                .concat("/")
                .concat(getMySQLLibraryName());
    }

    public boolean loadCache() throws Exception {
        File libraryFile = getMySQLLibraryFile();
        if (libraryFile.exists()) {
            if (getSha256(libraryFile).equals(MYSQL_SHA256)) {
                logger.info("Cached library verified, adding to classpath...");
                try {
                    ReflectUtil.addFileLibrary(libraryFile);
                } catch (Throwable e) {
                    logger.error("An exception occurred while adding to classpath", e);
                }
                logger.info("MySQL library successfully added to classpath");
                return true;
            }
            logger.warn("Cached MySQL dependency SHA256 check failed. Re-downloading...");
        }
        return false;
    }

    public void downloadAndLoad() throws Exception {
        logger.info("Downloading: " + getMySQLLibraryName());
        File libraryFile = getMySQLLibraryFile();
        Path path = new FileDownloadTask(getMySQLDownloadUrl(), libraryFile.toPath()).call();
        if (!getSha256(path.toFile()).equals(MYSQL_SHA256)) {
            throw new RuntimeException("Downloaded library failed SHA256 check");
        }
        logger.info("Download successful, adding library to classpath");
        try {
            ReflectUtil.addFileLibrary(libraryFile);
        } catch (Throwable e) {
            logger.error("An exception occurred while adding the library to the classpath", e);
        }
        logger.info("MySql successfully added to classpath");
    }

    private String getSha256(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buff = new byte[1024];
            int n;
            while ((n = fis.read(buff)) > 0) {
                baos.write(buff, 0, n);
            }
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(baos.toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte aByte : digest) {
                String temp = Integer.toHexString((aByte & 0xFF));
                if (temp.length() == 1) {
                    sb.append("0");
                }
                sb.append(temp);
            }
            return sb.toString();
        }
    }
}
