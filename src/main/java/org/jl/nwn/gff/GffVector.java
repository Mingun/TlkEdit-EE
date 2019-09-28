package org.jl.nwn.gff;

public class GffVector extends GffField<float[]> {

    private float[] data = new float[]{0,0,0};

    protected GffVector(String label) {
        super(label, Gff.VECTOR);
    }

    @Override
    public void setData(float[] data) {
        if (data.length != 3) {
            throw new IllegalArgumentException("Illegal array length, expected 3: " + data.length);
        }
        this.data = data;
    }

    @Override
    public float[] getData() {
        return data;
    }

    @Override
    public String toString(){
        final StringBuilder sb = new StringBuilder();
        sb.append(label).append(" (").append(getTypeName()).append(") : ");
        sb.append("(");
        sb.append(data[0]);
        sb.append(" ");
        sb.append(data[1]);
        sb.append(" ");
        sb.append(data[2]);
        sb.append(")");
        return sb.toString();
    }
}
