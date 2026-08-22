package com.tomas.cuaderno.files;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cuaderno.files")
public class FileProperties {
    private String root = "/var/lib/cuaderno/files";
    private long maxUserBytes = 1_073_741_824L;
    public String getRoot() { return root; }
    public void setRoot(String value) { root = value; }
    public long getMaxUserBytes() { return maxUserBytes; }
    public void setMaxUserBytes(long value) { maxUserBytes = value; }
}
