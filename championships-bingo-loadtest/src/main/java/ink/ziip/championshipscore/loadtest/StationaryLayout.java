package ink.ziip.championshipscore.loadtest;

final class StationaryLayout {
    private StationaryLayout() {
    }

    static Point dispersed(double anchorX, double anchorZ, double anchorAngle,
                           int member, double separation) {
        double half = separation / 2.0;
        double localX = member % 2 == 0 ? -half : half;
        double localZ = member / 2 % 2 == 0 ? -half : half;
        double cosine = Math.cos(anchorAngle);
        double sine = Math.sin(anchorAngle);
        return new Point(anchorX + localX * cosine - localZ * sine,
                anchorZ + localX * sine + localZ * cosine);
    }

    record Point(double x, double z) {
    }
}
