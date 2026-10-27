package at.marcelr.mradmin.health;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryFillTest {

    private record Point(long time) {
    }

    private static Point at(long time) {
        return new Point(time);
    }

    private static List<Long> times(List<Point> points) {
        return points.stream().map(Point::time).toList();
    }

    @Test
    void coarserPointsFillInBeforeTheFinerOnesStart() {
        // 5-minute points from before a restart, 10-second points since it.
        List<Point> coarse = List.of(at(0), at(300), at(600), at(900));
        List<Point> tens = List.of(at(700), at(710), at(720));
        assertEquals(List.of(0L, 300L, 600L, 700L, 710L, 720L), times(HistoryFill.fill(List.of(tens, coarse), 0, Point::time)));
    }

    @Test
    void onlyTheChosenRange() {
        List<Point> coarse = List.of(at(0), at(300), at(600));
        List<Point> tens = List.of(at(650), at(660));
        assertEquals(List.of(300L, 600L, 650L, 660L), times(HistoryFill.fill(List.of(tens, coarse), 200, Point::time)));
    }

    @Test
    void threeResolutions() {
        List<Point> coarse = List.of(at(0), at(300));
        List<Point> tens = List.of(at(400), at(410), at(420));
        List<Point> seconds = List.of(at(415), at(416), at(417));
        assertEquals(List.of(0L, 300L, 400L, 410L, 415L, 416L, 417L),
                times(HistoryFill.fill(List.of(seconds, tens, coarse), 0, Point::time)));
    }

    @Test
    void emptyFinerSourceTakesTheCoarserOnes() {
        List<Point> coarse = List.of(at(0), at(300));
        assertEquals(List.of(0L, 300L), times(HistoryFill.fill(List.of(List.of(), coarse), 0, Point::time)));
    }
}
