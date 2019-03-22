package sample.objects;

import com.sun.istack.internal.NotNull;

public class ConnectionInfo {
    private boolean isConnected;
    private Double voltage;
    private Double amperage;
    private Double dc;
    private Integer rpm;
    private String version;

    public ConnectionInfo(@NotNull boolean isConnected, Double voltage, Double amperage, Double dc, Integer rpm, String version) {
        this.isConnected = isConnected;
        this.voltage = voltage;
        this.amperage = amperage;
        this.dc = dc;
        this.rpm = rpm;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public Double getVoltage() {
        return voltage;
    }

    public Double getAmperage() {
        return amperage;
    }

    public Double getDc() {
        return dc;
    }

    public Integer getRpm() {
        return rpm;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "ConnectionInfo{" +
                "isConnected=" + isConnected +
                ", voltage=" + voltage +
                ", amperage=" + amperage +
                ", dc=" + dc +
                ", rpm=" + rpm +
                ", version='" + version + '\'' +
                '}';
    }
}
