package richter.julian.zhunet;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.jjoe64.graphview.series.Series;
import com.squareup.timessquare.CalendarPickerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class GraphActivity extends AppCompatActivity {

//  ############################
//  ####  Variables:        ####
//  ############################

    private static final String TAG = "GraphActivity";

    private DatabaseHelper db_helper;
    private SharedPreferences shared_preferences;

    private GraphView graph;

    private ArrayList<GraphRecord> array_all;
    private ArrayList<GraphRecord> array_red;
    private ArrayList<GraphRecord> array_yellow;
    private ArrayList<GraphRecord> array_green;
    private ArrayList<GraphRecord> array_guideline;

    private LineGraphSeries<DataPoint>   series_all;
    private LineGraphSeries<DataPoint>   series_red;
    private LineGraphSeries<DataPoint>   series_yellow;
    private LineGraphSeries<DataPoint>   series_green;
    private PointsGraphSeries<DataPoint> series_ignored;
    private PointsGraphSeries<DataPoint> series_guideline;
    private PointsGraphSeries<DataPoint> series_tap_action;

//  ############################
//  ####  Stored Data:      ####
//  ############################

    private int maxDataPoints;
    private int current_cycle;
    private int maxDaysShown;
    private int minDaysShown;
    private double maxTemperatureRangeShown;
    private double minTemperatureRangeShown;

//  ############################
//  ####  Lifecycle:        ####
//  ############################

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        Log.i(TAG, "ZhuNet started.");
        showToastMessage("Welcome Back!");

        setupGraph();
        setupSettingsButton();
        setupResetButton();
        setupCalendarButton();

    }

    @Override
    protected void onResume() {
        super.onResume();

        loadSharedPreferences();
        loadRecordsFromDb();

        setNotification();

//        tempTestData();

    }

//  ############################
//  ####  Graph View:       ####
//  ############################

    private void setupGraph() {

        db_helper = new DatabaseHelper(GraphActivity.this);
        shared_preferences = PreferenceManager.getDefaultSharedPreferences(GraphActivity.this);

        array_all       = new ArrayList<>();
        array_red       = new ArrayList<>();
        array_yellow    = new ArrayList<>();
        array_green     = new ArrayList<>();
        array_guideline = new ArrayList<>();

        graph = findViewById(R.id.graph);
        graph.getViewport().setScrollable(true);
        graph.getViewport().setScrollableY(true);
        graph.getLegendRenderer().setVisible(false);
        setGraphLabelFormat();

    }

    private void setGraphLabelFormat() {

        graph.getGridLabelRenderer().setLabelFormatter(
            // Inner Class.
            new DefaultLabelFormatter() {
                @Override
                public String formatLabel(double value, boolean isValueX) {
                    // format x values.
                    if (isValueX) {
                        if (value % 2 == 0) {
                            return super.formatLabel(value, true);
                        }
                        else {
                            return "";
                        }
                    }
                    // format y values.
                    else {
                        if (Math.round(value * 100) % 10 == 0) {
                            if(Math.round(value * 100) % 100 == 0) {
                                // 37    -> 37.00
                                return super.formatLabel(value, false) + ".00";
                            }
                            else {
                                // 37.5  -> 37.50
                                return super.formatLabel(value, false) + "0";
                            }
                        }
                        else {
                                // 37.45 -> 37.45
                            return super.formatLabel(value, false);
                        }
                    }
                }
            }
        );

    }

    private void addRecord(double day, double temperature) {

        temperature = Math.round(temperature * 20.0) / 20.0;

        // Check if existing Record can be updated.
        for (int i = 0; i < array_all.size(); i++) {
            if(array_all.get(i).getDay() == day) {
                db_helper.updateRecordTemperature(day, temperature, current_cycle);
                array_all.get(i).setTemperature(temperature);

                array_all = mergeSortArrayDay(array_all);
                analyseRecords();
                return;
            }
        }

        // Cannot update existing Record, add a new one.
        db_helper.addRecord(day, temperature, current_cycle);
        array_all.add(new GraphRecord(day, temperature));

        array_all = mergeSortArrayDay(array_all);
        analyseRecords();

    }

    private void deleteRecord(double day) {
        Calendar cycle_start;

        cycle_start = Calendar.getInstance();
        cycle_start.setTimeInMillis(db_helper.getCycleDate(current_cycle));

        for (int i = 0; i < array_all.size(); i++) {
            if(array_all.get(i).getDay() == day) {

                // Delete first Day if there is at least a second:
                // TODO: not working properly, date wrong?
                if (day == 1 && 1 < array_all.size()) {

                    // Delete Record from Database:
                    db_helper.deleteRecord(1, current_cycle);

                    // Update remaining Record Days for current 2nd Day to be Day 1:
                    for (int j = array_all.size() - 1; 0 <= j; j--) {
                        double old_day = array_all.get(j).getDay();
                        double new_day = array_all.get(j).getDay() - array_all.get(1).getDay() + 1;
                        db_helper.updateRecordDay(old_day, new_day, current_cycle);
                        array_all.get(j).setDay(new_day);
                    }

                    // Update Cycle Start Date with difference between 1st and 2nd Record:
                    cycle_start.add(Calendar.DAY_OF_MONTH, (int)array_all.get(1).getDay() - 1);
                    db_helper.updateCycleDate(current_cycle, cycle_start.getTimeInMillis());

                    // Delete Record from Array:
                    array_all.remove(0);
                    analyseRecords();
                    break;
                }

                // Delete other Days:
                else {
                    db_helper.deleteRecord(day, current_cycle);
                    array_all.remove(i);

                    analyseRecords();
                    break;
                }
            }
        }
    }

    private void hideRecord(double day) {

        for (int i = 0; i < array_all.size(); i++) {
            if (array_all.get(i).getDay() == day) {
                db_helper.updateRecordIgnored(day, 1, current_cycle);
                array_all.get(i).setIgnored(true);

                analyseRecords();
                break;
            }
        }
    }

    private void showRecord(double day, double temperature) {

        temperature = Math.round(temperature * 20.0) / 20.0;

        for (int i = 0; i < array_all.size(); i++) {
            if (array_all.get(i).getDay() == day) {
                db_helper.updateRecordIgnored(day, 0, current_cycle);
                array_all.get(i).setIgnored(false);

                addRecord(day, temperature);
                break;
            }
        }
    }

    /**
     * Finds a firstHighTemperature by comparing the current one with the highest from the
     * last 6 temperatures.
     * Starts the temperature analysis.
     */
    private void analyseRecords() {
        GraphRecord[] previous_records = new GraphRecord[6];
        GraphRecord   current_record;
        int records_set;
        int position;
        double highest_temperature;
        boolean[] highest_temperature_found = new boolean[4];

        highest_temperature_found[0] = false;
        highest_temperature_found[1] = false;
        highest_temperature_found[2] = false;
        highest_temperature_found[3] = false;
        records_set = 0;
        position = 0;

        // Save first 6 not ignored Records.
        while(records_set < 6) {
            if (position < array_all.size() - 1) {
                previous_records[records_set] = array_all.get(position);
                records_set++;
                position = getNextNotIgnoredPosition(position);
            }
            else {
                break;
            }
        }

        // Start analysing if 6 Records where saved.
        if(6 == records_set) {

            // Compare current value with previous 6 till analysis is done.
            for (int i = 6; i < array_all.size(); i++) {

                current_record = array_all.get(i);

                // Skip current_record if it should be ignored.
                if (!current_record.isIgnored()) {

                    // Get highest_temperature from last 6 valid Records.
                    highest_temperature = Math.max(previous_records[0].getTemperature(), previous_records[1].getTemperature());
                    highest_temperature = Math.max(previous_records[2].getTemperature(), highest_temperature);
                    highest_temperature = Math.max(previous_records[3].getTemperature(), highest_temperature);
                    highest_temperature = Math.max(previous_records[4].getTemperature(), highest_temperature);
                    highest_temperature = Math.max(previous_records[5].getTemperature(), highest_temperature);
                    // First higher temperature.
//                Log.i(TAG, "------------------------------");
//                Log.i(TAG, "day        : " + array_all.get(i).getDay());
//                Log.i(TAG, "temperature: " + highest_temperature);
                    if (highest_temperature < current_record.getTemperature() && !current_record.isIgnored()) {
                        /* hhH  */      highest_temperature_found[0] =  firstAnalysis(i, highest_temperature, highest_temperature_found);
                        /* hhhh */      highest_temperature_found[1] = secondAnalysis(i, highest_temperature, highest_temperature_found);
                        /* hLHh */      highest_temperature_found[2] =  thirdAnalysis(i, highest_temperature, highest_temperature_found);
                        /* hhLH */      highest_temperature_found[3] = fourthAnalysis(i, highest_temperature, highest_temperature_found);
                    }

                    // Update previous 6 temperatures for the next item.
                    previous_records[0] = previous_records[1];
                    previous_records[1] = previous_records[2];
                    previous_records[2] = previous_records[3];
                    previous_records[3] = previous_records[4];
                    previous_records[4] = previous_records[5];
                    previous_records[5] = current_record;

                }
            }
//            Log.i(TAG, "#######################");
//            Log.i(TAG, "##  Done Analysing.  ##");
//            Log.i(TAG, "#######################");
        }
        // First 6 temperatures cannot be analysed.
        else {
            array_yellow.clear();
            array_guideline.clear();
        }
        // Set changes to arrays depending on the set yellow array.
        setAnalysisChanges();
    }

    /**
     * Analyses the temperature. Looks for hhH.
     *
     * @param position                      Position in array at which first item was found. ( < 6 ) 
     * @param highest_temperature           highest_temperature found for the first item.
     * @param highest_temperature_found     True if any Analysis found a possible temperature
     *                                      to complete analysis.
     * @return                              True to block others if analysis did not fail yet or is complete.
     *                                      False if failed to reset.
     */
    private boolean firstAnalysis (int position, double highest_temperature, boolean[] highest_temperature_found) {
        GraphRecord start;
        GraphRecord first;
        GraphRecord second;
        GraphRecord third;

        // Only check if others did not find the first possible firstHighTemperaturePosition.
        if (!highest_temperature_found[1] && !highest_temperature_found[2] && !highest_temperature_found[3]) {
            start = array_all.get(position - 1);
            first = array_all.get(position);
            position = getNextNotIgnoredPosition(position);
            // Only check if itself found the first possible firstHighTemperaturePosition.
            if (!highest_temperature_found[0] || first == array_yellow.get(0)) {
                if (position < array_all.size()) {
                    second = array_all.get(position); // nextUnIgnoredRecord(position);
                    position = getNextNotIgnoredPosition(position);
                    if (highest_temperature < second.getTemperature()) {
                        if (position < array_all.size()) {
                            third = array_all.get(position);
                            // Calculate 0.20 for accuracy with "(highest_temperature * 5 + 1) / 5". 36.00+0.20=36.00.2!
                            if((highest_temperature * 5 + 1) / 5 <= third.getTemperature()) {
//                                Log.i(TAG, "h h H  : Third at least 0.20 higher. Done.");
                                array_yellow.clear();
                                array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                                array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                                array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                                array_yellow.add(new GraphRecord(third.getDay(), third.getTemperature()));
                                array_guideline.clear();
                                array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
//                                showToastMessage("Done. (h h H)");
                                Log.i(TAG, "Done. (h h H)");
                                return true;
                            }
                            else {
//                                Log.i(TAG, "h h H  : Third not 0.20 higher. Reset.");
                                array_yellow.clear();
                                array_guideline.clear();
                                return false;
                            }
                        }
                        else {
//                            Log.i(TAG, "h h H  : Second higher but no more data.");
                            array_yellow.clear();
                            array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                            array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                            array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                            array_guideline.clear();
                            array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                            return true;
                        }
                    }
                    else {
//                        Log.i(TAG, "h h H  : Second lower. Reset.");
                        array_yellow.clear();
                        array_guideline.clear();
                        return false;
                    }
                }
                else {
//                    Log.i(TAG, "h h H  : First higher but no more data.");
                    array_yellow.clear();
                    array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                    array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                    array_guideline.clear();
                    array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                    return true;
                }
            }
            else {
//                Log.i(TAG, "h h H  : Blocked by itself.");
                return true;
            }
        }
        else {
//            Log.i(TAG, "h h H  : Blocked by others.");
            return false;
        }
    }

    /**
     * Analyses the temperature. Looks for hhhh.
     *
     * @param position                      Position in array at which first item was found. ( < 6 ) 
     * @param highest_temperature           highest_temperature found for the first item.
     * @param highest_temperature_found     True if any Analysis found a possible temperature
     *                                      to complete analysis.
     * @return                              True to block others if analysis did not fail yet or is complete.
     *                                      False if failed to reset.
     */
    private boolean secondAnalysis(int position, double highest_temperature, boolean[] highest_temperature_found) {
        GraphRecord start;
        GraphRecord first;
        GraphRecord second;
        GraphRecord third;
        GraphRecord fourth;

        // Only check if others did not find the first possible firstHighTemperaturePosition.
        if (!highest_temperature_found[0] && !highest_temperature_found[2] && !highest_temperature_found[3]) {
            start = array_all.get(position - 1);
            first = array_all.get(position);
            position = getNextNotIgnoredPosition(position);
            // Only check if itself found the first possible firstHighTemperaturePosition.
            if (!highest_temperature_found[1] || first == array_yellow.get(0)) {
                if (position < array_all.size()) {
                    second = array_all.get(position);
                    position = getNextNotIgnoredPosition(position);
                    if (highest_temperature < second.getTemperature()) {
                        if (position < array_all.size()) {
                            third = array_all.get(position);
                            position = getNextNotIgnoredPosition(position);
                            if(highest_temperature < third.getTemperature()) {
                                if (position < array_all.size()) {
                                    fourth = array_all.get(position);
                                    if(highest_temperature < fourth.getTemperature()) {
//                                        Log.i(TAG, "h h h h: Fourth higher. Done.");
                                        array_yellow.clear();
                                        array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                                        array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                                        array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                                        array_yellow.add(new GraphRecord(third.getDay(), third.getTemperature()));
                                        array_yellow.add(new GraphRecord(fourth.getDay(), fourth.getTemperature()));
                                        array_guideline.clear();
                                        array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
//                                        showToastMessage("Done. (h h h h)");
                                        Log.i(TAG, "Done. (h h h h)");
                                        return true;
                                    }
                                    else {
//                                        Log.i(TAG, "h h h h: Fourth lower. Reset.");
                                        array_yellow.clear();
                                        array_guideline.clear();
                                        return false;
                                    }
                                }
                                else {
//                                    Log.i(TAG, "h h h h: Third higher but no more data.");
                                    array_yellow.clear();
                                    array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                                    array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                                    array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                                    array_yellow.add(new GraphRecord(third.getDay(), third.getTemperature()));
                                    array_guideline.clear();
                                    array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                                    return true;
                                }
                            }
                            else {
//                                Log.i(TAG, "h h h h: Third lower. Reset.");
                                array_yellow.clear();
                                array_guideline.clear();
                                return false;
                            }
                        }
                        else {
//                            Log.i(TAG, "h h h h: Second higher but no more data.");
                            array_yellow.clear();
                            array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                            array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                            array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                            array_guideline.clear();
                            array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                            return true;
                        }
                    }
                    else {
//                        Log.i(TAG, "h h h h: Second lower. Reset.");
                        array_yellow.clear();
                        array_guideline.clear();
                        return false;
                    }
                }
                else {
//                    Log.i(TAG, "h h h h: First higher but no more data.");
                    array_yellow.clear();
                    array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                    array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                    array_guideline.clear();
                    array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                    return true;
                }
            }
            else {
//                Log.i(TAG, "h h h h: Blocked by itself.");
                return true;
            }
        }
        else {
//            Log.i(TAG, "h h h h: Blocked by others.");
            return false;
        }
    }

    /**
     * Analyses the temperature. Looks for hLHh.
     *
     * @param position                      Position in array at which first item was found. ( < 6 ) 
     * @param highest_temperature           highest_temperature found for the first item.
     * @param highest_temperature_found     True if any Analysis found a possible temperature
     *                                      to complete analysis.
     * @return                              True to block others if analysis did not fail yet or is complete.
     *                                      False if failed to reset.
     */
    private boolean thirdAnalysis (int position, double highest_temperature, boolean[] highest_temperature_found) {
        GraphRecord start;
        GraphRecord first;
        GraphRecord second;
        GraphRecord third;
        GraphRecord fourth;

        // Only check if others did not find the first possible firstHighTemperaturePosition.
        if (!highest_temperature_found[0] && !highest_temperature_found[1] && !highest_temperature_found[3]) {
            start = array_all.get(position - 1);
            first = array_all.get(position);
            position = getNextNotIgnoredPosition(position);
            // Only check if itself found the first possible firstHighTemperaturePosition.
            if (!highest_temperature_found[2] || first == array_yellow.get(0)) {
                if (position < array_all.size()) {
                    second = array_all.get(position);
                    position = getNextNotIgnoredPosition(position);
                    if ((second.getTemperature() < highest_temperature)) {
                        if (position < array_all.size()) {
                            third = array_all.get(position);
                            position = getNextNotIgnoredPosition(position);
                            // Calculate 0.20 for accuracy with "(highest_temperature * 5 + 1) / 5". 36.00+0.20=36.00.2!
                            if ((highest_temperature * 5 + 1) / 5 <= third.getTemperature()) {
                                if (position < array_all.size()) {
                                    fourth = array_all.get(position);
                                    if (highest_temperature < fourth.getTemperature()) {
//                                        Log.i(TAG, "h L H h: Fourth higher. Done.");
                                        array_yellow.clear();
                                        array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                                        array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                                        array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                                        array_yellow.add(new GraphRecord(third.getDay(), third.getTemperature()));
                                        array_yellow.add(new GraphRecord(fourth.getDay(), fourth.getTemperature()));
                                        array_guideline.clear();
                                        array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
//                                        showToastMessage("Done. (h L H h)");
                                        Log.i(TAG, "Done. (h L H h)");

                                        return true;
                                    }
                                    else {
//                                        Log.i(TAG, "h L H h: Fourth not higher. Reset.");
                                        array_yellow.clear();
                                        array_guideline.clear();
                                        return false;
                                    }
                                }
                                else {
//                                    Log.i(TAG, "h L H h: Third at least 0.20 higher but no more data.");
                                    array_yellow.clear();
                                    array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                                    array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                                    array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                                    array_yellow.add(new GraphRecord(third.getDay(), third.getTemperature()));
                                    array_guideline.clear();
                                    array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                                    return true;
                                }
                            }
                            else {
//                                Log.i(TAG, "h L H h: Third not 0.20 higher. Reset.");
                                array_yellow.clear();
                                array_guideline.clear();
                                return false;
                            }
                        }
                        else {
//                            Log.i(TAG, "h L H h: Second lower but no more data.");
                            array_yellow.clear();
                            array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                            array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                            array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                            array_guideline.clear();
                            array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                            return true;
                        }
                    }
                    else {
//                        Log.i(TAG, "h L H h: Second higher. Reset.");
                        array_yellow.clear();
                        array_guideline.clear();
                        return false;
                    }
                }
                else {
//                    Log.i(TAG, "h L H h: First higher but no more data.");
                    array_yellow.clear();
                    array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                    array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                    array_guideline.clear();
                    array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                    return true;
                }
            }
            else {
//                Log.i(TAG, "h L H h: Blocked by itself.");
                return true;
            }
        }
        else {
//            Log.i(TAG, "h L H h: Blocked by others.");
            return false;
        }
    }

    /**
     * Analyses the temperature. Looks for hhLH.
     *
     * @param position                      Position in array at which first item was found. ( < 6 ) 
     * @param highest_temperature           highest_temperature found for the first item.
     * @param highest_temperature_found     True if any Analysis found a possible temperature
     *                                      to complete analysis.
     * @return                              True to block others if analysis did not fail yet or is complete.
     *                                      False if failed to reset.
     */
    private boolean fourthAnalysis(int position, double highest_temperature, boolean[] highest_temperature_found) {
        GraphRecord start;
        GraphRecord first;
        GraphRecord second;
        GraphRecord third;
        GraphRecord fourth;

        // Only check if others did not find the first possible firstHighTemperaturePosition.
        if (!highest_temperature_found[0] && !highest_temperature_found[1] && !highest_temperature_found[2]) {
            start = array_all.get(position - 1);
            first = array_all.get(position);
            position = getNextNotIgnoredPosition(position);
            // Only check if itself found the first possible firstHighTemperaturePosition.
            if (!highest_temperature_found[3] || first == array_yellow.get(0)) {
                if (position < array_all.size()) {
                    second = array_all.get(position);
                    position = getNextNotIgnoredPosition(position);
                    if (highest_temperature < second.getTemperature()) {
                        if (position < array_all.size()) {
                            third = array_all.get(position);
                            position = getNextNotIgnoredPosition(position);
                            if (third.getTemperature() < highest_temperature) {
                                if (position < array_all.size()) {
                                    fourth = array_all.get(position);
                                    if((highest_temperature * 5 + 1) / 5 <= fourth.getTemperature()) {
//                                        Log.i(TAG, "h h L H: Fourth at least 0.20 higher. Done.");
                                        array_yellow.clear();
                                        array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                                        array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                                        array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                                        array_yellow.add(new GraphRecord(third.getDay(), third.getTemperature()));
                                        array_yellow.add(new GraphRecord(fourth.getDay(), fourth.getTemperature()));
                                        array_guideline.clear();
                                        array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
//                                        showToastMessage("Done. (h h L H)");
                                        Log.i(TAG, "Done. (h h L H)");
                                        return true;
                                    }
                                    else {
//                                        Log.i(TAG, "h h L H: Fourth not at least 0.20 higher. Reset.");
                                        array_yellow.clear();
                                        array_guideline.clear();
                                        return false;
                                    }
                                }
                                else {
//                                    Log.i(TAG, "h h L H: Third lower but no more data.");
                                    array_yellow.clear();
                                    array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                                    array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                                    array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                                    array_yellow.add(new GraphRecord(third.getDay(), third.getTemperature()));
                                    array_guideline.clear();
                                    array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                                    return true;
                                }
                            }
                            else {
//                                Log.i(TAG, "h h L H: Third higher. Reset.");
                                array_yellow.clear();
                                array_guideline.clear();
                                return false;
                            }
                        }
                        else {
//                            Log.i(TAG, "h h L H: Second higher but no more data.");
                            array_yellow.clear();
                            array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                            array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                            array_yellow.add(new GraphRecord(second.getDay(), second.getTemperature()));
                            array_guideline.clear();
                            array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                            return true;
                        }
                    }
                    else {
//                        Log.i(TAG, "h h L H: Second lower. Reset.");
                        array_yellow.clear();
                        array_guideline.clear();
                        return false;
                    }
                }
                else {
//                    Log.i(TAG, "h h L H: First higher but no more data.");
                    array_yellow.clear();
                    array_yellow.add(new GraphRecord(start.getDay(), start.getTemperature()));
                    array_yellow.add(new GraphRecord(first.getDay(), first.getTemperature()));
                    array_guideline.clear();
                    array_guideline.add(new GraphRecord(first.getDay(), highest_temperature));
                    return true;
                }
            }
            else {
//                Log.i(TAG, "h h L H: Blocked by itself.");
                return true;
            }
        }
        else {
//            Log.i(TAG, "h h L H: Blocked by others.");
            return false;
        }
    }

    /**
     * Returns the next Position for a Record in the array_all for the Analysis
     * that is not supposed to be ignored.
     * If a non valid position (too high) is returned, the analysis stops.
     *
     * @param position                  Current Position.
     * @return position                 Next not ignored Record Position
     */
    private int getNextNotIgnoredPosition(int position) {

        position++;

        // Increment and check.
        while (position < array_all.size()) {
            // Return if not ignored.
            if (!array_all.get(position).isIgnored()) {
                return position;
            }
            position++;
        }

        // Return non valid position for Analysis to stop.
        return position;

    }

    /**
     * Clears the Red Green and GuideLine Array to add values back in.
     * Red will be filled if the analysis did not find a firstHighMaxTemperature.
     * Otherwise Red will be filled till first Yellow X and Green will be filled from last Yellow X.
     * GuideLine will always draw a 0.50 margin to the left of the lowest and to the right of the highest temperature.
     * GuideLine will be drawn at found firstHighMaxTemperature with guideLineTemperature.
     */
    private void setAnalysisChanges() {
        double day;
        double temperature;

        array_red.clear();
        array_green.clear();

        for (int i = 0; i < array_all.size(); i++) {
            // Ignore hidden Records.
            if (!array_all.get(i).isIgnored()) {

                day         = array_all.get(i).getDay();
                temperature = array_all.get(i).getTemperature();

                if (array_yellow.isEmpty()) {
                    // Fill Red if Temperature Analysis is not done.
                    array_red.add(new GraphRecord(day, temperature));
                }
                else {
                    // Add temperatures till first Yellow to Red.
                    if (day <= array_yellow.get(0).getDay()) {
                        array_red.add(new GraphRecord(day, temperature));
                    }
                    // Add temperatures from last Yellow to Green.
                    if (array_yellow.get(array_yellow.size() - 1).getDay() <= day ) {
                        array_green.add(new GraphRecord(day, temperature));
                    }
                }

            }

        }

        // Sort arrays to prevent errors.
        array_red    = mergeSortArrayDay(array_red);
        array_yellow = mergeSortArrayDay(array_yellow);
        array_green  = mergeSortArrayDay(array_green);

        drawGraph();
    }

    private void drawGraph() {
        // Reset Graph before editing:
        graph.removeAllSeries();

        // Edit Series:
        setSeriesSettings();
        fillSeries();
        fillGuideline();

        // Add Series after Edit is done:
        addSeries();

        // Display Utility:
        setViewport();
        setGraphTitle();

    }

    private void setSeriesSettings() {
        series_all = new LineGraphSeries<>();
        series_all.setTitle("Temperature");
        series_all.setDrawDataPoints(true);
        series_all.setDataPointsRadius(15);

        series_red = new LineGraphSeries<>();
        series_red.setTitle("Red");
        series_red.setColor(Color.parseColor("#44FF0000"));
        series_red.setDrawBackground(true);
        series_red.setBackgroundColor(Color.parseColor("#44FF0000"));

        series_yellow = new LineGraphSeries<>();
        series_yellow.setTitle("Yellow");
        series_yellow.setColor(Color.parseColor("#44FFFF00"));
        series_yellow.setDrawBackground(true);
        series_yellow.setBackgroundColor(Color.parseColor("#44FFFF00"));

        series_green = new LineGraphSeries<>();
        series_green.setTitle("Green");
        series_green.setColor(Color.parseColor("#4400FF00"));
        series_green.setDrawBackground(true);
        series_green.setBackgroundColor(Color.parseColor("#4400FF00"));

        series_ignored = new PointsGraphSeries<>();
        series_ignored.setTitle("Ignored");
        series_ignored.setSize(15);
        series_ignored.setColor(Color.parseColor("#44646464"));

        series_guideline = new PointsGraphSeries<>();
        series_guideline.setTitle("Guideline");
        series_guideline.setSize(5);
        series_guideline.setColor(Color.parseColor("#44000000"));

        series_tap_action = new PointsGraphSeries<>();
        series_tap_action.setTitle("Ignored");
        series_tap_action.setSize(15);
        series_tap_action.setColor(Color.parseColor("#44646464"));
        series_tap_action.setOnDataPointTapListener(
            // Inner Class.
            new OnDataPointTapListener() {
                @Override
                public void onTap(Series series, DataPointInterface dataPoint) {
                    // Find clicked Graph Record:
                    for (int i = 0; i < array_all.size(); i++) {
                        if (dataPoint.getX() == array_all.get(i).getDay()) {
                            // series_all:
                            if(!array_all.get(i).isIgnored()) {
                                UpdateTemperatureDialogBox(array_all.get(i));
                                break;
                            }
                            // ignored_series:
                            else {
                                IgnoredTemperatureDialogBox(array_all.get(i));
                                break;
                            }
                        }
                    }
                }
            }
        );
    }

    private void fillSeries() {
        double day;
        double temperature;

        array_all       = mergeSortArrayDay(array_all);
        array_red       = mergeSortArrayDay(array_red);
        array_yellow    = mergeSortArrayDay(array_yellow);
        array_green     = mergeSortArrayDay(array_green);
        array_guideline = mergeSortArrayDay(array_guideline);

        // Fill Series with data from arrays.
        for(int i = 0; i < array_red.size(); i++) {
            day         = array_red.get(i).getDay();
            temperature = array_red.get(i).getTemperature();
            series_red.appendData(new DataPoint(day, temperature), true, maxDataPoints);
        }
        for(int i = 0; i < array_yellow.size(); i++) {
            day         = array_yellow.get(i).getDay();
            temperature = array_yellow.get(i).getTemperature();
            series_yellow.appendData(new DataPoint(day, temperature), true, maxDataPoints);
        }
        for(int i = 0; i < array_green.size(); i++) {
            day         = array_green.get(i).getDay();
            temperature = array_green.get(i).getTemperature();
            series_green.appendData(new DataPoint(day, temperature), true, maxDataPoints);
        }
        for (int i = 0; i < array_all.size(); i++) {
            day         = array_all.get(i).getDay();
            temperature = array_all.get(i).getTemperature();
            series_tap_action.appendData(new DataPoint(day, temperature), true, maxDataPoints);

            if (!array_all.get(i).isIgnored()) {
                series_all.appendData(new DataPoint(day, temperature), true, maxDataPoints);
            }
            else {
                series_ignored.appendData(new DataPoint(day, temperature), true, maxDataPoints);
            }
        }

    }

    private void fillGuideline() {
        double day;
        double temperature;
        double temperature_guideline;
        double temperature_lowest;
        double temperature_highest;

        // If Guideline Point was set, draw the line.
        if (!array_guideline.isEmpty() && !array_all.isEmpty()) {

            temperature_guideline = array_guideline.get(0).getTemperature();
            temperature_lowest    = mergeSortArrayTemperature(array_all).get(0)
                    .getTemperature();
            temperature_highest   = mergeSortArrayTemperature(array_all).get(array_all.size() - 1)
                    .getTemperature();
            array_guideline.clear();

            // Line:
            for (double i = array_all.get(0).getDay();
                 i <= array_all.get(array_all.size() - 1).getDay();
                 i = i + 0.50) {

                array_guideline.add(new GraphRecord(i, temperature_guideline));
            }
            // Bottom Left  Padding:
            array_guideline.add(new GraphRecord(array_all.get(0)
                    .getDay() - 0.50,
                    temperature_lowest - 0.05));
            // Top    Right Padding:
            array_guideline.add(new GraphRecord(array_all.get(array_all.size() - 1)
                    .getDay() + 0.50,
                    temperature_highest + 0.05));

        }

        // If there is not Record, draw Padding:
        if (array_all.isEmpty()) {

            temperature_lowest  = 36.00 - 0.05;
            temperature_highest = 36.50 + 0.05;

            // Bottom Left  Padding:
            array_guideline.add(new GraphRecord(1 - 0.50, temperature_lowest));
            // Top    Right Padding:
            array_guideline.add(new GraphRecord(5 + 0.50, temperature_highest));

        }

        // Sort Final Guideline Array.
        array_guideline = mergeSortArrayDay(array_guideline);

        for(int i = 0; i < array_guideline.size(); i++) {
            day         = array_guideline.get(i).getDay();
            temperature = array_guideline.get(i).getTemperature();
            series_guideline.appendData(new DataPoint(day, temperature), true, maxDataPoints);
        }

    }

    private void addSeries() {

        // Add series that are not empty to the graph in correct order.
        if(!array_guideline.isEmpty()) {
            graph.addSeries(series_guideline);
        }
        if(!series_red.isEmpty()) {
            graph.addSeries(series_red);
        }
        if(!series_yellow.isEmpty()) {
            graph.addSeries(series_yellow);
        }
        if(!series_green.isEmpty()) {
            graph.addSeries(series_green);
        }
        if(!series_all.isEmpty()) {
            graph.addSeries(series_all);
        }
        if(!series_ignored.isEmpty()) {
            graph.addSeries(series_ignored);
        }
        if(!series_tap_action.isEmpty()) {
            graph.addSeries(series_tap_action);
        }

    }

    private void setViewport() {
        ArrayList<GraphRecord> boundArray;
        double highestX;
        double lowestX;
        double highestY;
        double lowestY;

        double upperBoundX;
        double lowerBoundX;
        int numVerticalLines;

        double upperBoundY;
        double lowerBoundY;
        int numHorizontalLines;

                        /* Set manual Viewport for X. */

        boundArray = mergeSortArrayDay(array_all);
        lowestX = 1;

        if(!boundArray.isEmpty()) {
            highestX = boundArray.get(boundArray.size() - 1).getDay();
            lowestX  = boundArray.get(0).getDay();
            if (highestX < minDaysShown && highestX - lowestX < maxDaysShown) {
                upperBoundX      = lowestX + minDaysShown - 1 + 0.50;
                lowerBoundX      = lowestX                    - 0.50;
                numVerticalLines = minDaysShown              + 1   ;
            }
            else {
                if (highestX < maxDaysShown && highestX - lowestX < maxDaysShown) {
                    upperBoundX      =              highestX           + 0.50;
                    lowerBoundX      =              lowestX            - 0.50;
                    numVerticalLines = (int) ( (1 + highestX - lowestX) + 1  );
                }
                else {
                    upperBoundX      = highestX                    + 0.50;
                    lowerBoundX      = highestX - maxDaysShown + 1 - 0.50;
                    numVerticalLines = maxDaysShown               + 1   ;
                }
            }
        }
        else {
            upperBoundX      = 1 + minDaysShown - 1 + 0.50;
            lowerBoundX      = 1                    - 0.50;
            numVerticalLines = minDaysShown         + 1   ;
        }

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMaxX(upperBoundX);
        graph.getViewport().setMinX(lowerBoundX);
        //graph.getGridLabelRenderer().setHorizontalAxisTitle("Day");
        graph.getGridLabelRenderer().setNumHorizontalLabels(numVerticalLines);

                        /* Set manual Viewport for Y. */

        boundArray = mergeSortArrayTemperature(array_all);
        lowestY = 36.00;

        if(!boundArray.isEmpty()) {
            highestY = boundArray.get(boundArray.size() - 1).getTemperature();
            lowestY  = boundArray.get(0).getTemperature();
            if (highestY - lowestY < minTemperatureRangeShown) {
                upperBoundY        = lowestY + minTemperatureRangeShown  + 0.05;
                lowerBoundY        = lowestY                             - 0.05;
                numHorizontalLines = (int) ( ( minTemperatureRangeShown + 0.10 ) / 0.05);
            }
            else {
                if (highestY - lowestY < maxTemperatureRangeShown) {
                    upperBoundY        = highestY                             + 0.05;
                    lowerBoundY        = lowestY                              - 0.05;
                    numHorizontalLines = (int) ( ( (0.05 + highestY - lowestY) + 0.10 ) / 0.05);
                }
                else {
                    upperBoundY        = highestY                            + 0.05;
                    lowerBoundY        = highestY - maxTemperatureRangeShown - 0.05;
                    numHorizontalLines = (int) ( ( maxTemperatureRangeShown + 0.10 ) / 0.05);
                }
            }
        }
        else {
            upperBoundY      = 36.00 + minTemperatureRangeShown - 0.05   + 0.05;
            lowerBoundY      = 36.00                                     - 0.05;
            numHorizontalLines = (int) ( ( minTemperatureRangeShown      + 0.10 ) / 0.05);
        }

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMaxY(upperBoundY);
        graph.getViewport().setMinY(lowerBoundY);
        graph.getGridLabelRenderer().setVerticalAxisTitle("Temperature");
        graph.getGridLabelRenderer().setNumVerticalLabels(numHorizontalLines);

    }

    /**
     * Sets the Graph Title with given current_cycle.
     * Sets Day by calculating what today's day is in respect to the cycle Start Date.
     */
    private void setGraphTitle() {
        double day;
        Calendar today;
        Calendar cycle_start;

        day = 1;

        today       = Calendar.getInstance();
        cycle_start = Calendar.getInstance();

        if (!array_all.isEmpty()) {

            cycle_start.setTimeInMillis(db_helper.getCycleDate(current_cycle));

            while (cycle_start.get(Calendar.MONTH)        != today.get(Calendar.MONTH) ||
                   cycle_start.get(Calendar.DAY_OF_MONTH) != today.get(Calendar.DAY_OF_MONTH) ) {

                today.add(Calendar.DAY_OF_MONTH, -1);
                day++;
            }
        }

        graph.setTitle("Cycle: " + current_cycle + ", Day: " + ((int) day));
    }

//  ############################
//  ####  Dialog Calendar:  ####
//  ############################

    /**
     * Button functionality to open the CalendarDialogBox.
     */
    private void setupCalendarButton() {
        Button btn_calendar;

        btn_calendar = findViewById(R.id.btn_calendar);
        btn_calendar.setOnClickListener(
            // Inner class.
            new View.OnClickListener() {

                @Override
                public void onClick(View view) {

                    CalendarDialogBox();

                }
            }
        );
    }

    /**
     * Opens a Calendar Date Picker Dialog.
     * Previously entered Dates are highlightet by calculating the Cycle start + Day from Record.
     * Checks if selected Date should be updated or newly added.
     */
    private void CalendarDialogBox(){
        Calendar today;
        Calendar min_month_shown;
        Calendar max_month_shown;
        Calendar previously_entered_dates;
        CalendarPickerView calendar_view;
        CalendarPickerView.FluentInitializer initializer;
        AlertDialog.Builder dialog_builder;
        View dialog_view;
        final AlertDialog dialog_calendar;

        today                    = Calendar.getInstance();
        min_month_shown          = Calendar.getInstance();
        max_month_shown          = Calendar.getInstance();
        previously_entered_dates = Calendar.getInstance();

        min_month_shown.add(Calendar.MONTH,-2);
        max_month_shown.add(Calendar.MONTH,2);

        dialog_builder = new AlertDialog.Builder(GraphActivity.this);
        dialog_view = getLayoutInflater().inflate(R.layout.dialog_graph_calendar, null);
        dialog_builder.setView(dialog_view);
        dialog_calendar = dialog_builder.create();
        dialog_calendar.show();

        calendar_view = dialog_calendar.findViewById(R.id.calendar_view);
        initializer = calendar_view.init(min_month_shown.getTime(), max_month_shown.getTime());
        initializer.withSelectedDate(today.getTime());

        // Calculate entered Dates with starting Day + Day from Record (starting at 1 not 0).
        for (int i = 0; i < array_all.size(); i++) {
            previously_entered_dates.setTimeInMillis(db_helper.getCycleDate(current_cycle));
            previously_entered_dates.add(Calendar.DAY_OF_MONTH, (int)array_all.get(i).getDay() - 1);

            initializer.withHighlightedDate(previously_entered_dates.getTime());
        }

        calendar_view.setOnDateSelectedListener(
            // Inner class.
            new CalendarPickerView.OnDateSelectedListener() {

                @Override
                public void onDateSelected(Date date) {
                    Calendar updatable_date;
                    Calendar selected_date;
                    boolean updated;

                    updatable_date = Calendar.getInstance();
                    selected_date  = Calendar.getInstance();
                    selected_date.setTimeInMillis(date.getTime());

                    // First Record for that Cycle.
                    if (array_all.isEmpty()) {
                        AddTemperatureDialogBox(selected_date);
                    }
                    else {
                        // Check if Record should be updated or newly added.
                        updated = false;
                        for (int i = 0; i < array_all.size(); i++) {
                            updatable_date.setTimeInMillis(db_helper.getCycleDate(current_cycle));
                            updatable_date.add(Calendar.DAY_OF_MONTH, (int)array_all.get(i).getDay() - 1);
                            // Update:
                            if (updatable_date.get(Calendar.MONTH)        == selected_date.get(Calendar.MONTH)       &&
                                updatable_date.get(Calendar.DAY_OF_MONTH) == selected_date.get(Calendar.DAY_OF_MONTH)  ) {

                                UpdateTemperatureDialogBox(array_all.get(i));
                                updated = true;
                                break;
                            }
                        }
                        // Add:
                        if (!updated) {
                            AddTemperatureDialogBox(selected_date);
                            Log.i(TAG, "Add date:\n" + selected_date.getTime());
                        }
                    }

                    dialog_calendar.dismiss();

                }

                @Override
                public void onDateUnselected(Date date) {

                }

            }
        );

    }

//  ############################
//  ####  Dialog Add:       ####
//  ############################

    /**
     * Dialog to add a new Record to the Graph.
     * Button Date opens the CalendarDialogBox again.
     * Button Cancel closes the Dialog.
     * Button Add adds a Record based on the selected Date.
     *
     * @param selected_date             Date for a new Graph Record.
     */
    private void AddTemperatureDialogBox(final Calendar selected_date) {
        final ListView list_pre_decimal;
        final ListView list_decimal;
        AlertDialog.Builder dialog_builder;
        View dialog_view;
        AlertDialog dialog_add_temperature;
        double last_temperature;

        dialog_builder = new AlertDialog.Builder(GraphActivity.this);
        dialog_view = getLayoutInflater().inflate(R.layout.dialog_graph_temperature_slider, null);
        dialog_builder.setView(dialog_view);
        dialog_add_temperature = dialog_builder.create();
        dialog_add_temperature.setTitle("Add Temperature");
        dialog_add_temperature.setMessage("Enter the temperature you have measured.");

        // Setup List Display with Utility Methods.
        if (array_all.isEmpty()) {
            last_temperature = 36.50;
        }
        else {
            last_temperature = array_all.get(array_all.size() - 1).getTemperature();
        }
        list_pre_decimal = dialog_view.findViewById(R.id.list_pre_decimal);
        setupPreDecimalList(list_pre_decimal, last_temperature);
        list_decimal     = dialog_view.findViewById(R.id.list_decimal);
        setupDecimalList(list_decimal, last_temperature);

        dialog_add_temperature.setButton(Dialog.BUTTON_NEUTRAL, "Date",
                // Inner class.
                new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CalendarDialogBox();

                        dialog.dismiss();
                    }
                }
        );

        dialog_add_temperature.setButton(Dialog.BUTTON_NEGATIVE, "Cancel",
                // Inner class.
                new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();
                    }
                }
        );

        dialog_add_temperature.setButton(Dialog.BUTTON_POSITIVE, "Add",
                // Inner class.
                new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Object list_item;
                        double pre_decimal_value;
                        double decimal_value;
                        double temperature;

                        // Get the current center value from the Temperature Slider.
                        list_item = list_pre_decimal.getItemAtPosition(list_pre_decimal.getFirstVisiblePosition() + 1);
                        pre_decimal_value = Double.parseDouble(list_item.toString().replaceAll("[^.0123456789]", ""));
                        list_item = list_decimal    .getItemAtPosition(list_decimal    .getFirstVisiblePosition() + 1);
                        decimal_value     = Double.parseDouble(list_item.toString().replaceAll("[^.0123456789]", ""));
                        temperature = pre_decimal_value + decimal_value;

                        setSelectedDateDay(temperature, selected_date);

                        dialog.dismiss();
                    }
                }
        );

        dialog_add_temperature.show();
    }

    /**
     * Calculates what Day was selected and adds it to the Graph.
     *
     * @param temperature               Temperature for the new Record.
     * @param selected_date             Date of the new Record.
     */
    private void setSelectedDateDay(double temperature, Calendar selected_date) {
        int days_passed;
        Calendar cycle_start;

        cycle_start   = Calendar.getInstance();
        cycle_start.setTimeInMillis(db_helper.getCycleDate(current_cycle));

        // First Record for that Cycle.
        if (array_all.isEmpty()) {
            cycle_start.setTimeInMillis(selected_date.getTimeInMillis());
            db_helper.updateCycleDate(current_cycle, cycle_start.getTimeInMillis());
            addRecord(1, temperature);
        }
        else {
            // Date is after Cycle Start. Add after first day.
            if(cycle_start.getTimeInMillis() < selected_date.getTimeInMillis()) {
                days_passed = 0;
                while (cycle_start.get(Calendar.MONTH)        != selected_date.get(Calendar.MONTH) ||
                       cycle_start.get(Calendar.DAY_OF_MONTH) != selected_date.get(Calendar.DAY_OF_MONTH) ) {

                    cycle_start.add(Calendar.DAY_OF_MONTH, 1);
                    days_passed++;
                }
                addRecord(array_all.get(0).getDay() + days_passed, temperature);
            }
            // Date is before Cycle Start. Shift existing days into the future and update Cycle Date.
            else {
                days_passed = 0;
                while (cycle_start.get(Calendar.MONTH)        != selected_date.get(Calendar.MONTH) ||
                       cycle_start.get(Calendar.DAY_OF_MONTH) != selected_date.get(Calendar.DAY_OF_MONTH) ) {

                    cycle_start.add(Calendar.DAY_OF_MONTH, -1);
                    days_passed++;
                }

                for (int i = array_all.size() - 1; 0 <= i; i--) {
                    double old_day = array_all.get(i).getDay();
                    double new_day = array_all.get(i).getDay() + days_passed;
                    db_helper.updateRecordDay(old_day, new_day, current_cycle);
                    array_all.get(i).setDay(new_day);
                }

                db_helper.updateCycleDate(current_cycle, selected_date.getTimeInMillis());
                addRecord(1, temperature);
            }
        }
    }

//  ############################
//  ####  Dialog Update:    ####
//  ############################

    /**
     * Updates an existing Graph Record's Temperature.
     *
     * @param selected_record               Record that was selected.
     */
    private void UpdateTemperatureDialogBox(final GraphRecord selected_record) {
        final ListView list_pre_decimal;
        final ListView list_decimal;
        AlertDialog.Builder dialog_builder;
        View dialog_view;
        AlertDialog dialog_add_temperature;

        dialog_builder = new AlertDialog.Builder(GraphActivity.this);
        dialog_view = getLayoutInflater().inflate(R.layout.dialog_graph_temperature_slider, null);
        dialog_builder.setView(dialog_view);
        dialog_add_temperature = dialog_builder.create();
        dialog_add_temperature.setTitle("Update Day " + (int)selected_record.getDay() + ".");
        dialog_add_temperature.setMessage("Enter the temperature you have measured.");

        // Setup List Display with Utility Methods.
        list_pre_decimal = dialog_view.findViewById(R.id.list_pre_decimal);
        setupPreDecimalList(list_pre_decimal, selected_record.getTemperature());
        list_decimal     = dialog_view.findViewById(R.id.list_decimal);
        setupDecimalList(list_decimal, selected_record.getTemperature());

        dialog_add_temperature.setButton(Dialog.BUTTON_NEUTRAL, "Hide",
                // Inner class.
                new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        hideRecord(selected_record.getDay());

                        dialog.dismiss();
                    }
                }
        );

        dialog_add_temperature.setButton(Dialog.BUTTON_NEGATIVE, "Cancel",
                // Inner class.
                new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();
                    }
                }
        );

        dialog_add_temperature.setButton(Dialog.BUTTON_POSITIVE, "Update",
                // Inner class.
                new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Object list_item;
                        double pre_decimal_value;
                        double decimal_value;
                        double temperature;

                        // Get the current center value from the Temperature Slider.
                        list_item = list_pre_decimal.getItemAtPosition(list_pre_decimal.getFirstVisiblePosition() + 1);
                        pre_decimal_value = Double.parseDouble(list_item.toString().replaceAll("[^.0123456789]", ""));
                        list_item = list_decimal    .getItemAtPosition(list_decimal    .getFirstVisiblePosition() + 1);
                        decimal_value     = Double.parseDouble(list_item.toString().replaceAll("[^.0123456789]", ""));
                        temperature = pre_decimal_value + decimal_value;

                        addRecord(selected_record.getDay(), temperature);

                        dialog.dismiss();
                    }
                }
        );

        dialog_add_temperature.show();
    }

//  ############################
//  ####  Dialog Ignored:   ####
//  ############################

    /**
     * Show an ignored Record with updated Temperature.
     *
     * @param selected_record               Record that was selected.
     */
    private void IgnoredTemperatureDialogBox(final GraphRecord selected_record) {
        final ListView list_pre_decimal;
        final ListView list_decimal;
        AlertDialog.Builder dialog_builder;
        View dialog_view;
        AlertDialog dialog_add_temperature;

        dialog_builder = new AlertDialog.Builder(GraphActivity.this);
        dialog_view = getLayoutInflater().inflate(R.layout.dialog_graph_temperature_slider, null);
        dialog_builder.setView(dialog_view);
        dialog_add_temperature = dialog_builder.create();
        dialog_add_temperature.setTitle("Update Day " + (int)selected_record.getDay() + ". (Ignored)");
        dialog_add_temperature.setMessage("Enter the temperature you have measured.");

        // Setup List Display with Utility Methods.
        list_pre_decimal = dialog_view.findViewById(R.id.list_pre_decimal);
        setupPreDecimalList(list_pre_decimal, selected_record.getTemperature());
        list_decimal     = dialog_view.findViewById(R.id.list_decimal);
        setupDecimalList(list_decimal, selected_record.getTemperature());

        dialog_add_temperature.setButton(Dialog.BUTTON_NEUTRAL, "Delete",
                // Inner class.
                new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        deleteRecord(selected_record.getDay());

                        dialog.dismiss();
                    }
                }
        );

        dialog_add_temperature.setButton(Dialog.BUTTON_NEGATIVE, "Cancel",
                // Inner class.
                new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();
                    }
                }
        );

        dialog_add_temperature.setButton(Dialog.BUTTON_POSITIVE, "Show",
                // Inner class.
                new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Object list_item;
                        double pre_decimal_value;
                        double decimal_value;
                        double temperature;

                        // Get the current center value from the Temperature Slider.
                        list_item = list_pre_decimal.getItemAtPosition(list_pre_decimal.getFirstVisiblePosition() + 1);
                        pre_decimal_value = Double.parseDouble(list_item.toString().replaceAll("[^.0123456789]", ""));
                        list_item = list_decimal    .getItemAtPosition(list_decimal    .getFirstVisiblePosition() + 1);
                        decimal_value     = Double.parseDouble(list_item.toString().replaceAll("[^.0123456789]", ""));
                        temperature = pre_decimal_value + decimal_value;

                        showRecord(selected_record.getDay(), temperature);

                        dialog.dismiss();
                    }
                }
        );

        dialog_add_temperature.show();
    }

//  ############################
//  ####  Display Utility:  ####
//  ############################

    private void setupPreDecimalList(ListView list, double last_temperature) {
        ArrayList<String> listItems;
        GraphTemperatureItemAdapter adapter;
        String item;
        Object lastTemperatureItem;
        double currentTemperature;

        // Fill Array with objects.
        listItems = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            item = String.format((Locale) null, "   %02d   ", i + 25);
            listItems.add(item);
        }

        // Create adapter and set it to the list.
        adapter = new GraphTemperatureItemAdapter(GraphActivity.this, R.layout.item_graph_temperature, listItems);
        list.setAdapter(adapter);

        list.setVerticalScrollBarEnabled(false);
        setupSnappingScrollListener(list);
        setupListSize(list);

        for (int i = 0; i < adapter.getCount(); i++) {

            lastTemperatureItem = list.getItemAtPosition(i);
            currentTemperature  = Double.parseDouble(lastTemperatureItem.toString().replaceAll("[^.0123456789]", ""));

            if ((int) (currentTemperature) == (int) (last_temperature)) {
                list.setSelection(i - 1);
                break;
            }
        }
    }

    private void setupDecimalList(ListView list, double last_temperature) {
        ArrayList<String> listItems;
        GraphTemperatureItemAdapter adapter;
        String item;
        Object lastTemperatureItem;
        double currentTemperature;

        // Fill Array with objects.
        listItems = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            item = String.format((Locale) null, "  .%02d °C", i);
            listItems.add(item);
        }

        // Create adapter and set it to the list.
        adapter = new GraphTemperatureItemAdapter(GraphActivity.this, R.layout.item_graph_temperature, listItems);
        list.setAdapter(adapter);

        list.setVerticalScrollBarEnabled(false);
        setupSnappingScrollListener(list);
        setupListSize(list);

        // Only Take Decimal part with two digits.
        last_temperature = last_temperature - (int) last_temperature;
        last_temperature = (double) Math.round(last_temperature * 100) / 100;

        for (int i = 0; i < adapter.getCount(); i++) {

            lastTemperatureItem = list.getItemAtPosition(i);
            currentTemperature = Double.parseDouble(lastTemperatureItem.toString().replaceAll("[^.0123456789]", ""));

            if (currentTemperature == last_temperature) {
                list.setSelection(i - 1);
                break;
            }
        }
    }

    private void setupSnappingScrollListener(ListView list) {
        // Scroll Listener to snap to center object.
        list.setOnScrollListener(
            // Inner class.
            new AbsListView.OnScrollListener() {
                boolean scrolled;
                int itemHiddenPart;
                int itemShownPart;
                int snapPosition;

                @Override
                public void onScrollStateChanged(AbsListView list, int scrollState) {
                    if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                        // Scrolled by user input.
                        scrolled = true;
                    }

                    if (scrollState == SCROLL_STATE_IDLE) {

                        // Only scroll after user input.
                        if (scrolled) {
                            scrolled = false;

                            // Get the first visible child (item) from the parent (listView).
                            View firstItem = list.getChildAt(0);
                            itemHiddenPart = firstItem.getTop() * (-1);     // Hidden is negative.
                            itemShownPart = firstItem.getBottom();          // Shown  is positive.

                            if (itemHiddenPart < itemShownPart) {
                                snapPosition = list.getFirstVisiblePosition();
                                list.setSelection(snapPosition);
                            }
                            else {
                                snapPosition = list.getFirstVisiblePosition() + 1;
                                list.setSelection(snapPosition);
                            }
                        }
                    }
                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                }
            }
        );
    }

    private void setupListSize(final ListView list) {
        // Post runs after layout has be drawn, resize the list to only show 3 items.
        list.post(
            // Inner class.
            new Runnable() {
                View listItem;
                int itemHeight;
                int dividerHeight;

                @Override
                public void run() {
                    ViewGroup.LayoutParams params;

                    ListAdapter listAdapter = list.getAdapter();
                    if (listAdapter != null) {
                        // Get height of first item and one divider.
                        listItem = list.getChildAt(0);
                        itemHeight = listItem.getHeight();
                        dividerHeight = list.getDividerHeight();

                        // Set listView changes.
                        params = list.getLayoutParams();
                        params.height = itemHeight * 3 + dividerHeight * 2;
                        list.setFadingEdgeLength(itemHeight);
                        list.setVerticalFadingEdgeEnabled(true);
                        list.setLayoutParams(params);
                    }
                }
            }
        );
    }

//  ############################
//  ####  Dialog Reset:     ####
//  ############################

    /**
     * Button functionality to open the ResetDialogBox.
     */
    private void setupResetButton() {
        Button btn_reset;

        btn_reset = findViewById(R.id.btn_reset);
        btn_reset.setOnClickListener(
            new View.OnClickListener() {

                @Override
                public void onClick(View view) {

                    ResetDialogBox();

                }
            }
        );
    }

    /**
     * Dialog to reset the Graph.
     * Discard deletes all shown Records from the Graph and Database.
     * Cancel just closes the Dialog.
     * Save saves the shown Records in the Database, clears the Graph and increments the current_cycle.
     */
    private void ResetDialogBox() {
        AlertDialog.Builder dialog_builder;
        AlertDialog dialog_reset;

        // Create a dialogBuilder.
        dialog_builder = new AlertDialog.Builder(GraphActivity.this);

        dialog_reset = dialog_builder.create();
        dialog_reset.setTitle("Reset");
        dialog_reset.setMessage("Do you want to save or discard the current data?");

        dialog_reset.setButton(Dialog.BUTTON_NEUTRAL, "Discard",
            // Inner class.
            new DialogInterface.OnClickListener(){

                @Override
                public void onClick(DialogInterface dialog, int which) {

                    db_helper.deleteCycleRecords(current_cycle);
                    // Clear Graph View.
                    array_all.clear();
                    analyseRecords();

                    dialog.dismiss();
                }
            }
        );

        dialog_reset.setButton(Dialog.BUTTON_NEGATIVE, "Cancel",
            // Inner class.
            new DialogInterface.OnClickListener(){

                @Override
                public void onClick(DialogInterface dialog, int which) {

                    dialog.dismiss();
                }
            }
        );

        dialog_reset.setButton(Dialog.BUTTON_POSITIVE, "Save",
            // Inner class.
            new DialogInterface.OnClickListener(){

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SharedPreferences.Editor shared_preferences_editor;

                    // Increment current_cycle.
                    current_cycle = db_helper.getHighestCycle() + 1;
                    shared_preferences_editor = shared_preferences.edit();
                    shared_preferences_editor.putString(getString(R.string.current_cycle),"" + current_cycle);
                    shared_preferences_editor.apply();

                    // Clear Graph View.
                    array_all.clear();
                    analyseRecords();

                    dialog.dismiss();
                }
            }
        );

        dialog_reset.show();

    }

//  ############################
//  ####  Dialog Settings:  ####
//  ############################

    private void setupSettingsButton() {
        Button btn_settings;

        btn_settings = findViewById(R.id.btn_settings);
        btn_settings.setOnClickListener(
                // Inner Class.
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {

                        tempTestDatabase();

                        //Intent intent = SettingsActivity.makeIntent(GraphActivity.this);
                        //startActivity(intent);
                        //tempTestData();
                        //testViewAll();

                    }
                }
        );

    }

//  ############################
//  ####  Database:         ####
//  ############################

    private void loadSharedPreferences() {

        maxDataPoints = 365;

        current_cycle = Integer.parseInt(shared_preferences.getString(getString(R.string.current_cycle), "1"));

        maxDaysShown  = Integer.parseInt(shared_preferences.getString(getString(R.string.maxDaysShown), "15"));
        minDaysShown  = Integer.parseInt(shared_preferences.getString(getString(R.string.minDaysShown), "5"));
        maxTemperatureRangeShown = Double.parseDouble(shared_preferences.getString(getString(R.string.maxTemperatureRangeShown), "1.00"));
        minTemperatureRangeShown = Double.parseDouble(shared_preferences.getString(getString(R.string.minTemperatureRangeShown), "0.50"));

    }

    /**
     * Loads data from the currently set current_cycle and draws the Graph.
     */
    private void loadRecordsFromDb() {
        double day;
        double temperature;
        int is_ignored;
        Cursor data;

        data = db_helper.getRecords(current_cycle);

        if ((data != null) && (data.getCount() > 0)) {
            while (data.moveToNext()) {
                day         = data.getDouble(1);
                temperature = data.getDouble(2);
                is_ignored  = data.getInt   (3);

                if (is_ignored == 0) {
                    array_all.add(new GraphRecord(day, temperature));
                }
                else {
                    array_all.add(new GraphRecord(day, temperature, true));
                }
            }
            array_all     = mergeSortArrayDay(array_all);
        }

        analyseRecords();
    }

//  ############################
//  ####  Notification:     ####
//  ############################

    private void setNotification() {
        Intent        intent_alarm;
        PendingIntent pending_intent_alarm;
        AlarmManager  alarm_manager;

        // Time for the Notification:
        Calendar alarm_time = Calendar.getInstance();
        alarm_time.set(Calendar.HOUR_OF_DAY, 6);
        alarm_time.set(Calendar.MINUTE, 0);

        // Intent to start the Notification Receiver.
        intent_alarm = new Intent(GraphActivity.this, GraphNotificationReceiver.class);

        // Wrap into Pending Intent for Alarm Manager.
        pending_intent_alarm = PendingIntent.getBroadcast(
                GraphActivity.this,
                0,
                intent_alarm,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Execute Pending Intent at given time and repeat daily.
        alarm_manager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if(alarm_manager != null){
            alarm_manager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    alarm_time.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pending_intent_alarm);
        }

    }

//  ############################
//  ####  Utility Methods:  ####
//  ############################

    /**
     * Method to sort an ArrayList of GraphRecord items in
     * ascending order by Day with MergeSort.
     *
     * @param unsortedArray         ArrayList to be sorted.
     * @return sortedArray          Given Arraylist sorted.
     */
    private ArrayList<GraphRecord> mergeSortArrayDay(ArrayList<GraphRecord> unsortedArray) {
        ArrayList<GraphRecord> sortedArray;
        int countedElements;
        int centerPosition;
        int currentLeft;
        int currentRight;
        double dayLeft;
        double dayRight;

        countedElements = unsortedArray.size();

        // Array with one element is always sorted.
        if (countedElements < 2) {
            sortedArray = unsortedArray;
            return sortedArray;
        }
        // Split Array from center if it has more than two Elements.
        else {
            centerPosition = unsortedArray.size() / 2;
            ArrayList<GraphRecord> leftArray  = new ArrayList<>();
            ArrayList<GraphRecord> rightArray = new ArrayList<>();
            // Fill Arrays.
            for (int i = 0; i < centerPosition; i++ ) {
                leftArray.add(unsortedArray.get(i));
            }
            for (int i = centerPosition; i < countedElements; i++) {
                rightArray.add(unsortedArray.get(i));
            }
            // Recursive call of left and right arrays till there is only one element left.
            leftArray  = mergeSortArrayDay(leftArray);
            rightArray = mergeSortArrayDay(rightArray);

            // Merge resulting left and right array by comparing Date value of first elements.
            sortedArray = new ArrayList<>();
            currentLeft = 0;
            currentRight = 0;
            while (currentLeft < leftArray.size() && currentRight < rightArray.size()) {
                dayLeft  = leftArray.get(currentLeft).getDay();
                dayRight = rightArray.get(currentRight).getDay();
                // Merge ascending ( lower first )
                if (dayLeft < dayRight) {
                    sortedArray.add(leftArray.get(currentLeft));
                    currentLeft++;
                }
                else {
                    sortedArray.add(rightArray.get(currentRight));
                    currentRight++;
                }
            }
            // Add remaining from the remaining array at the end.
            while (currentLeft  < leftArray.size()) {
                sortedArray.add(leftArray.get(currentLeft));
                currentLeft++;
            }
            while (currentRight < rightArray.size()) {
                sortedArray.add(rightArray.get(currentRight));
                currentRight++;
            }
            return sortedArray;
        }
    }

    /**
     * Method to sort an ArrayList of GraphRecord items in
     * ascending order by Temperature with MergeSort.
     *
     * @param unsortedArray         ArrayList to be sorted.
     * @return sortedArray          Given Arraylist sorted.
     */
    private ArrayList<GraphRecord> mergeSortArrayTemperature(ArrayList<GraphRecord> unsortedArray) {
        ArrayList<GraphRecord> sortedArray;
        int countedElements;
        int centerPosition;
        int currentLeft;
        int currentRight;
        double xLeft;
        double xRight;

        countedElements = unsortedArray.size();

        // Array with one element is always sorted.
        if (countedElements < 2) {
            return unsortedArray;
        }
        // Split Array from center if it has more than two Elements.
        else {
            centerPosition = unsortedArray.size() / 2;
            ArrayList<GraphRecord> leftArray  = new ArrayList<>();
            ArrayList<GraphRecord> rightArray = new ArrayList<>();
            // Fill Arrays.
            for (int i = 0; i < centerPosition; i++ ) {
                leftArray.add(unsortedArray.get(i));
            }
            for (int i = centerPosition; i < countedElements; i++) {
                rightArray.add(unsortedArray.get(i));
            }
            // Recursive call of left and right arrays till there is only one element left.
            leftArray = mergeSortArrayTemperature(leftArray);
            rightArray = mergeSortArrayTemperature(rightArray);

            // Merge final left and right array by comparing X value of first elements.
            sortedArray = new ArrayList<>();
            currentLeft = 0;
            currentRight = 0;
            while (currentLeft < leftArray.size() && currentRight < rightArray.size()) {
                xLeft  = leftArray.get(currentLeft).getTemperature();
                xRight = rightArray.get(currentRight).getTemperature();
                // Merge ascending ( lower first )
                if (xLeft < xRight) {
                    sortedArray.add(leftArray.get(currentLeft));
                    currentLeft++;
                }
                else {
                    sortedArray.add(rightArray.get(currentRight));
                    currentRight++;
                }
            }
            // Add remaining from the remaining array at the end.
            while (currentLeft  < leftArray.size()) {
                sortedArray.add(leftArray.get(currentLeft));
                currentLeft++;
            }
            while (currentRight < rightArray.size()) {
                sortedArray.add(rightArray.get(currentRight));
                currentRight++;
            }
            return sortedArray;
        }
    }

    /**
     * Creates an Intent to start this Activity from another.
     *
     * @param context       Context of the calling Activity.
     * @return intent       Intent to start this Activity.
     */
    public static Intent makeIntent(Context context) {
        return new Intent(context, GraphActivity.class);
    }

    /**
     * Displays a given message as a short Toast popup.
     *
     * @param message           Message to be shown.
     */
    private void showToastMessage(String message) {
        Toast.makeText(GraphActivity.this, message, Toast.LENGTH_SHORT).show();
    }

//  ############################
//  ####  Testing:          ####
//  ############################

    private void tempTestData() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -32);

        db_helper.deleteAllCycles();

        addRecord(1, 36.40);
        addRecord(2, 36.60);
        addRecord(3, 36.50);
        addRecord(4, 36.30);
        addRecord(5, 36.50);
        addRecord(6, 36.55);
        addRecord(7, 36.50);
        addRecord(8, 36.90);
        addRecord(9, 36.50);
        addRecord(10, 36.60);
        addRecord(11, 36.50);
        addRecord(12, 36.50);
        addRecord(13, 36.55);
        addRecord(14, 36.40);
        addRecord(15, 36.55);
        addRecord(16, 36.80);
        addRecord(17, 36.90);
        addRecord(18, 36.80);
        addRecord(19, 36.80);
        addRecord(20, 36.85);
        addRecord(21, 36.75);
        addRecord(22, 36.95);
        addRecord(23, 36.95);
        addRecord(24, 37.00);
        addRecord(25, 36.85);
        addRecord(26, 36.80);
        addRecord(27, 36.85);
        addRecord(28, 36.75);
        addRecord(29, 36.70);

        db_helper.updateCycleDate(1, cal.getTimeInMillis());



    }

    private void tempTestDatabase() {
        Calendar cycle_start = Calendar.getInstance();
        cycle_start.setTimeInMillis(db_helper.getCycleDate(1));

        showToastMessage(
                "Cycle Start: " + cycle_start.get(Calendar.MONTH) + "." + cycle_start.get(Calendar.DAY_OF_MONTH) + "\n" +
                "Cycle Count: " + db_helper.getCycleCount()                  + "\n" +
                "Records Count:" + db_helper.getAllRecordCount()             + "\n" +
                "Record(1) Count: " + db_helper.getRecordCount(1)
        );


    }

}
