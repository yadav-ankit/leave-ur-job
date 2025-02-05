package com.nifty.util;

import java.util.Vector;
import java.util.stream.IntStream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;

public class SuperTrendIndicator {

    private BarSeries series;
    private double multiplier;
    private int length;
    private ATRIndicator atri;

    private Vector<Double> values; // Vector for possibility to use parallel stream
    private Vector<Double> upperBands;
    private Vector<Double> lowerBands;
    private Vector<Boolean> isGreen;

    public SuperTrendIndicator(BarSeries series, Double multiplier, int length) {
        this.multiplier = multiplier;
        this.length = length;
        this.series = series;
        calculate();
    }

    public SuperTrendIndicator(BarSeries series) {
        this(series, 3.0, 10);
    }

    public SuperTrendIndicator() {
    }


    public SuperTrendIndicator(BarSeries series, Double multiplier) {
        this(series, multiplier, 10);
    }

    public void calculate() {
        setAtri(new ATRIndicator(getSeries(), getLength()));
        values = new Vector<>(series.getBarCount());
        upperBands = new Vector<>(series.getBarCount());
        lowerBands = new Vector<>(series.getBarCount());
        isGreen = new Vector<>(series.getBarCount());

        IntStream.range(0, series.getBarCount()).forEach(i -> {
            values.add(null);
            upperBands.add(null);
            lowerBands.add(null);
            isGreen.add(true);
        });
        IntStream.range(length, series.getBarCount()).forEach(i -> values.set(i, get(i)));
    }

    public Double getValue(int index) {
        return values.get(index);
    }

    private double get(int index) {
        double finalBand;
        isGreen.set(index, new Boolean(isGreen.get(index - 1)));
        if (isGreen.get(index - 1)) {
            finalBand = finalLowerBand(index);
            if (getSeries().getBar(index).getClosePrice().doubleValue() <= finalBand) {
                isGreen.set(index, false);
                return finalUpperBand(index);
            }
        } else {
            finalBand = finalUpperBand(index);
            if (getSeries().getBar(index).getClosePrice().doubleValue() >= finalBand) {
                isGreen.set(index, true);
                return finalLowerBand(index);
            }
        }
        return finalBand;
    }

    // calculation upperBand
    public double finalUpperBand(int index) {
        double band;
        if (index < length)
            return -1;
        if (upperBands.get(index) != null)
            return upperBands.get(index);
        double atrVal = getAtri().getValue(index).doubleValue();

        double max = getSeries().getBarData().get(index).getHighPrice().doubleValue();
        double min = getSeries().getBarData().get(index).getLowPrice().doubleValue();
        double upperBand = ((max + min) / 2) + (getMultiplier() * atrVal);
        double fubPrev = finalUpperBand(index - 1);

        if (fubPrev == -1 || upperBand < fubPrev || getSeries().getBar(index - 1).getClosePrice().doubleValue() > fubPrev) {
            band = upperBand;
        } else {
            band = fubPrev;
        }
        upperBands.set(index, band); // for not to calculate again and again
        return band;
    }

    // calculation lowerBand
    public double finalLowerBand(int index) {
        double band;
        if (lowerBands.get(index) != null)
            return lowerBands.get(index);
        if (index < length)
            return -1;
        double atrVal = getAtri().getValue(index).doubleValue();
        double max = getSeries().getBarData().get(index).getHighPrice().doubleValue();
        double min = getSeries().getBarData().get(index).getLowPrice().doubleValue();
        double lowerBand = (max + min) / 2 - (multiplier * atrVal);
        double flbPrev = finalLowerBand(index - 1);

        if (flbPrev == -1 || lowerBand > flbPrev || getSeries().getBar(index - 1).getClosePrice().doubleValue() < flbPrev) {
            band = lowerBand;
        } else {
            band = flbPrev;
        }
        lowerBands.set(index, band); // for not to calculate again and again
        return band;
    }

    public BarSeries getSeries() {
        return series;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public int getLength() {
        return length;
    }

    public void setSeries(BarSeries series) {
        this.series = series;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public void setLength(int length) {
        this.length = length;
    }

    private ATRIndicator getAtri() {
        return atri;
    }

    private void setAtri(ATRIndicator atri) {
        this.atri = atri;
    }

    public boolean getIsGreen(int index) {
        return isGreen.get(index);
    }

    public String getSignal(int index) {
        if (getIsGreen(index) == getIsGreen(index - 1))
            return "";
        if (getIsGreen(index))
            return "Buy";
        return "Sell";
    }

}
