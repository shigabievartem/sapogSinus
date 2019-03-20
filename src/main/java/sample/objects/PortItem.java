package sample.objects;

import java.util.Objects;

import static java.lang.String.format;
import static sample.utils.SapogUtils.isBlankOrNull;

public class PortItem {
    private final String portNum;
    private String description;

    public PortItem(String portNum, String description) {
        this.portNum = portNum;
        this.description = description;
    }

    public String getPortNum() {
        return portNum;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return !isBlankOrNull(description) ? format("[%s] " + description, portNum) : String.valueOf(portNum);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PortItem portItem = (PortItem) o;
        return Objects.equals(portNum, portItem.portNum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portNum);
    }
}
