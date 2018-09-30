package richter.julian.zhunet;

public class GraphRecord {

    private double day;
    private double temperature;
    private boolean ignored;

    public GraphRecord(double day, double temperature, boolean ingored) {
        this(day, temperature);
        this.ignored = ingored;
    }

    public GraphRecord(double day, double temperature) {
        this.day = day;
        this.temperature = temperature;
        this.ignored = false;
    }

    public double getDay() {
        return this.day;
    }

    public void setDay(double day) {
        this.day = day;
    }

    public double getTemperature() {
        return this.temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean isIgnored() {
        return this.ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

}
