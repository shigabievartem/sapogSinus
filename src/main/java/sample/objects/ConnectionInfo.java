package sample.objects;

import com.sun.istack.internal.NotNull;

public class ConnectionInfo {
    public ConnectionInfo(@NotNull boolean isConnected, Double voltage, Double amperage, Double dc, Integer rpm) {
        this.isConnected = isConnected;
        this.voltage = voltage;
        this.amperage = amperage;
        this.dc = dc;
        this.rpm = rpm;
    }

    private boolean isConnected;
    private Double voltage;
    private Double amperage;
    private Double dc;
    private Integer rpm;

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

    @Override
    public String toString() {
        return "ConnectionInfo{" +
                "isConnected=" + isConnected +
                ", voltage=" + voltage +
                ", amperage=" + amperage +
                ", dc=" + dc +
                ", rpm=" + rpm +
                '}';
    }
}
