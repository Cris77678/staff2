package me.drex.staffmod.config;

public class JailZone {

    public String name;
    public String dimension;
    public double x1, y1, z1;
    public double x2, y2, z2;
    public double spawnX, spawnY, spawnZ;

    public JailZone(String name, String dimension,
                    double x1, double y1, double z1,
                    double x2, double y2, double z2) {
        this.name = name;
        this.dimension = dimension;
        this.x1 = Math.min(x1, x2); this.x2 = Math.max(x1, x2);
        this.y1 = Math.min(y1, y2); this.y2 = Math.max(y1, y2);
        this.z1 = Math.min(z1, z2); this.z2 = Math.max(z1, z2);
        this.spawnX = (this.x1 + this.x2) / 2.0;
        this.spawnY = this.y1;
        this.spawnZ = (this.z1 + this.z2) / 2.0;
    }

    public boolean contains(double x, double y, double z) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    public double[] clamp(double x, double y, double z) {
        return new double[]{
            Math.max(x1, Math.min(x2, x)),
            Math.max(y1, Math.min(y2, y)),
            Math.max(z1, Math.min(z2, z))
        };
    }
}
