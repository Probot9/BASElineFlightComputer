package com.platypii.baseline.views.charts;

import com.platypii.baseline.measurements.MLocation;
import com.platypii.baseline.util.Bounds;
import com.platypii.baseline.util.DataSeries;
import android.content.Context;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import java.util.List;

public class TimeChart extends PlotView {

    private static final int AXIS_ALT = 0;
    private static final int AXIS_SPEED = 1;
    private static final int AXIS_GLIDE = 2;

    private List<MLocation> trackData;

    private final DataSeries altitudeSeries = new DataSeries();
    private final DataSeries speedSeries = new DataSeries();
    private final DataSeries glideSeries = new DataSeries();

    public TimeChart(Context context, AttributeSet attrs) {
        super(context, attrs);

        final float density = getResources().getDisplayMetrics().density;
        options.padding.top = (int) (4 * density);
        options.padding.bottom = (int) (4 * density);

        options.axis.x = PlotOptions.axisTime();
        options.axis.y = PlotOptions.axisSpeed();

        // Initialize bounds with 3 axes
        plot.initBounds(3);
    }

    public void loadTrack(List<MLocation> trackData) {
        this.trackData = trackData;

        // Load track data into individual time series
        altitudeSeries.reset();
        speedSeries.reset();
        glideSeries.reset();
        for(MLocation loc : trackData) {
            altitudeSeries.addPoint(loc.millis, loc.altitude_gps);
            speedSeries.addPoint(loc.millis, loc.totalSpeed());
            glideSeries.addPoint(loc.millis, loc.glideRatio());
        }
    }

    @Override
    public void drawData(@NonNull Plot plot) {
        if (trackData != null) {
            // Draw track data
            drawTrackData(plot);
        } else {
            text.setTextAlign(Paint.Align.CENTER);
            plot.canvas.drawText("no track data", plot.width / 2, plot.height / 2, text);
        }
    }

    /**
     * Draw track data points
     */
    private void drawTrackData(@NonNull Plot plot) {
        // Draw data series
        paint.setColor(0xffff0000);
        plot.drawLine(AXIS_ALT, altitudeSeries, 1.5f, paint);
        paint.setColor(0xff0000ff);
        plot.drawLine(AXIS_SPEED, speedSeries, 1.5f, paint);
        paint.setColor(0xff7f00ff);
        plot.drawLine(AXIS_GLIDE, glideSeries, 1.5f, paint);
    }

    @NonNull
    @Override
    public Bounds getBounds(@NonNull Bounds dataBounds, int axis) {
        if (axis == AXIS_GLIDE) {
            dataBounds.y.min = 0;
            dataBounds.y.max = 4;
        }
        // Else scale to match data
        return dataBounds;
    }

}
