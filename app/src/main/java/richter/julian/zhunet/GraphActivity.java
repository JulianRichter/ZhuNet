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
import android.os.SystemClock;
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

import javax.security.auth.login.LoginException;

public class GraphActivity extends AppCompatActivity {

//  ############################
//  ####  Variables:        ####
//  ############################

    private static final String TAG = "GraphActivity";

    private DatabaseHelper db_helper;
    private SharedPreferences shared_preferences;

    private GraphView graph;

    // Current Graph Data:
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

    // Old Graph Data:
    private ArrayList<GraphRecord> array_old_all;
    private ArrayList<GraphRecord> array_old_red;
    private ArrayList<GraphRecord> array_old_yellow;
    private ArrayList<GraphRecord> array_old_green;

    private LineGraphSeries<DataPoint>   series_old_all;
    private LineGraphSeries<DataPoint>   series_old_red;
    private LineGraphSeries<DataPoint>   series_old_yellow;
    private LineGraphSeries<DataPoint>   series_old_green;

//  ############################
//  ####  Stored Data:      ####
//  ############################

    private int maxDataPoints;
    private int current_cycle;
    private int maxDaysShown;
    private int minDaysShown;
    private double maxTemperatureRangeShown;

//  ############################
//  ####  Lifecycle:        ####
//  ############################

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        Log.i(TAG, "ZhuNet started.");
        showToastMessage("Welcome Back!");

        setupSettingsButton();
        setupResetButton();
        setupCalendarButton();

    }

    @Override
    protected void onResume() {
        super.onResume();

        setupGraph();
        loadSharedPreferences();
        loadRecordsFromDb();

        setNotification();

//        tempTestData();

    }

//  ############################
//  ####  Database:         ####
//  ############################

    private void loadSharedPreferences() {

        maxDataPoints = 365;

        current_cycle = Integer.parseInt(shared_preferences.getString(getString(R.string.current_cycle), "1"));

        maxDaysShown  = Integer.parseInt(shared_preferences.getString(getString(R.string.maxDaysShown), "15"));
        minDaysShown  = Integer.parseInt(shared_preferences.getString(getString(R.string.minDaysShown), "3"));
        maxTemperatureRangeShown = Double.parseDouble(shared_preferences.getString(getString(R.string.maxTemperatureRangeShown), "1.00"));

        // TODO: Just for testing:
//        current_cycle =  2;
//        minDaysShown  =  3;
//        maxDaysShown  = 10;

    }

    /**
     * Loads data from the currently set current_cycle and draws the Graph.
     */
    private void loadRecordsFromDb() {
        double day;
        double temperature;
        int is_ignored;
        Cursor data;

        // Current cycle:
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
            array_all = mergeSortArrayDay(array_all);
        }
        analyseRecords();

        // Old cycle:
//        data = db_helper.getRecords(0);
        data = db_helper.getRecords(current_cycle - 1);
        if ((data != null) && (data.getCount() > 0)) {
            while (data.moveToNext()) {
                day         = data.getDouble(1);
                temperature = data.getDouble(2);
                is_ignored  = data.getInt   (3);
                if (is_ignored == 0) {
                    array_old_all.add(new GraphRecord(day, temperature));
                }
            }
            array_old_all = mergeSortArrayDay(array_old_all);

            // Shift Old days into the past:
            double offset = 0;

            if (!array_old_all.isEmpty()) {
                offset = offset + array_old_all.get(array_old_all.size() - 1).getDay();
            }

            for (int i = 0; i < array_old_all.size(); i++) {
                day = array_old_all.get(i).getDay();
                array_old_all.get(i).setDay(day - offset);
            }
        }
        analyseOldRecords();

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

        array_old_all       = new ArrayList<>();
        array_old_red       = new ArrayList<>();
        array_old_yellow    = new ArrayList<>();
        array_old_green     = new ArrayList<>();

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
                        if (value > -1) {
                            // Current cycle days:
                            if (value % 2 == 0) {
                                return super.formatLabel(value, true);
                            }
                            else {
                                return "";
                            }
                        }
                        else {
                            // Old cycle days are negative:
                            if (!array_old_all.isEmpty()) {
                                if (value % 2 == 0) {

                                    value = value - array_old_all.get(0).getDay();
                                    return super.formatLabel(value, true);
                                }
                                else {
                                    return "";
                                }
                            }
                            else {
                                return "";
                            }
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
//                            return ""; // Not working if ViewPort puts xx.x5 at top/bottom, needs bigger jumps
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

//  ############################
//  ####  Analyse Graph:    ####
//  ############################

    private void analyseRecords() {
        double highest_temperature;
        GraphRecord   current_record;
        GraphRecord[] previous_records = new GraphRecord[6];
        ArrayList<GraphRecord> array_not_ignored = new ArrayList<>();;

        // Fill array with not ignored records:
        for (int i = 0; i < array_all.size(); i++) {
            if (!array_all.get(i).isIgnored()) {
                array_not_ignored.add(array_all.get(i));
            }
        }

        // Start analysing if there are at least 7 Records:
        if (array_not_ignored.size() > 6) {

            previous_records[0] = array_not_ignored.get(0);
            previous_records[1] = array_not_ignored.get(1);
            previous_records[2] = array_not_ignored.get(2);
            previous_records[3] = array_not_ignored.get(3);
            previous_records[4] = array_not_ignored.get(4);
            previous_records[5] = array_not_ignored.get(5);

            for (int i = 6; i < array_not_ignored.size(); i++) {
                // Get highest_temperature from last 6 Records:
                highest_temperature = Math.max(previous_records[0].getTemperature(), previous_records[1].getTemperature());
                highest_temperature = Math.max(previous_records[2].getTemperature(), highest_temperature);
                highest_temperature = Math.max(previous_records[3].getTemperature(), highest_temperature);
                highest_temperature = Math.max(previous_records[4].getTemperature(), highest_temperature);
                highest_temperature = Math.max(previous_records[5].getTemperature(), highest_temperature);

                // Check if current temperature is higher than last highest_temperature found:
                current_record = array_not_ignored.get(i);

                if (highest_temperature < current_record.getTemperature()) {
                    if (analysisDone(array_not_ignored, i, highest_temperature)) {
                        break;
                    }
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
        // First 6 temperatures cannot be analysed.
        else {
            array_yellow.clear();
        }
        // Set changes to arrays depending on the set yellow array.
        setAnalysisChanges();

    }

    private boolean analysisDone(ArrayList<GraphRecord> array_not_ignored, int i, double highest_temperature) {
        GraphRecord previous;
        GraphRecord first;
        GraphRecord second;
        GraphRecord third;
        GraphRecord fourth;

        previous = array_not_ignored.get(i - 1);
        first    = array_not_ignored.get(i);

        if (i++ < array_not_ignored.size() - 1) {
            // h?..
            second = array_not_ignored.get(i);
            if (highest_temperature < second.getTemperature()) {
                // hh..
                if (i++ < array_not_ignored.size() - 1) {
                    // hh?.
                    third = array_not_ignored.get(i);
                    if ( (highest_temperature * 5 + 1) / 5 <= third.getTemperature() ) {
                        // hhH. - Done
//                        Log.i(TAG, "(h h H .) - Done!");
                        array_yellow.clear();
                        array_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                        array_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                        array_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                        array_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                        return true;
                    }
                    else {
                        if (highest_temperature < third.getTemperature()) {
                            // hhh.
                            if (i++ < array_not_ignored.size() - 1) {
                                // hhh?
                                fourth = array_not_ignored.get(i);
                                if (highest_temperature < fourth.getTemperature()) {
                                    // hhhh - Done
//                                    Log.i(TAG, "(h h h h) - Done!");
                                    array_yellow.clear();
                                    array_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                                    array_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                                    array_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                                    array_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                                    array_yellow.add(new GraphRecord(fourth.getDay()  , fourth.getTemperature()));
                                    return true;
                                }
                                else {
                                    // hhhL - Reset
//                                    Log.i(TAG, "(h h h L) - Reset!");
                                    array_yellow.clear();
                                    return false;
                                }
                            }
                            else {
                                // hhhx - No More
//                                Log.i(TAG, "(h h h x) - No more Records!");
                                array_yellow.clear();
                                array_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                                array_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                                array_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                                array_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                                return true;
                            }
                        }
                        else {
                            // hhL.
                            if (i++ < array_not_ignored.size() - 1) {
                                // hhL? (? Has to be at least 0.20 higher)
                                fourth = array_not_ignored.get(i);
                                if ( (highest_temperature * 5 + 1) / 5 <= fourth.getTemperature() ) {
                                    // hhLH - Done
//                                    Log.i(TAG, "(h h L H) - Done!");
                                    array_yellow.clear();
                                    array_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                                    array_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                                    array_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                                    array_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                                    array_yellow.add(new GraphRecord(fourth.getDay()  , fourth.getTemperature()));
                                    return true;
                                }
                                else {
                                    // hhLL - Reset
//                                    Log.i(TAG, "(h h L L) - Reset! (Not 0.20 higher)");
                                    array_yellow.clear();
                                    return false;
                                }
                            }
                            else {
                                // hhL. - No More
//                                Log.i(TAG, "(h h L .) - No more Records!");
                                array_yellow.clear();
                                array_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                                array_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                                array_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                                array_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                                return true;
                            }
                        }
                    }
                }
                else {
                    // hhx. - No More
//                    Log.i(TAG, "(h h x .) - No more Records!");
                    array_yellow.clear();
                    array_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                    array_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                    array_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                    return true;
                }
            }
            else {
                // hL..
                if (i++ < array_not_ignored.size() - 1) {
                    // hL?. (? Has to be at least 0.20 higher)
                    third = array_not_ignored.get(i);
                    if ( (highest_temperature * 5 + 1) / 5 <= third.getTemperature() ) {
                        // hLH.
                        if (i++ < array_not_ignored.size() - 1) {
                            // hLH?
                            fourth = array_not_ignored.get(i);
                            if (highest_temperature < fourth.getTemperature()) {
                                // hLHh - Done
//                                Log.i(TAG, "(h L H h) - Done!");
                                array_yellow.clear();
                                array_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                                array_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                                array_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                                array_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                                array_yellow.add(new GraphRecord(fourth.getDay()  , fourth.getTemperature()));
                                return true;
                            }
                            else {
                                // hLHL - Reset
//                                Log.i(TAG, "(h L H L) - Reset!");
                                array_yellow.clear();
                                return false;
                            }
                        }
                        else {
                            // hLHx - No more
//                            Log.i(TAG, "(h L H x) - No more Records!");
                            array_yellow.clear();
                            array_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                            array_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                            array_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                            array_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                            return true;
                        }
                    }
                    else {
                        // hLL. - Reset
//                        Log.i(TAG, "(h L L .) - Reset! (Not 0.20 higher)");
                        array_yellow.clear();
                        return false;
                    }
                }
                else {
                    // hLx. - No more
//                    Log.i(TAG, "(h L x .) - No more Records!");
                    array_yellow.clear();
                    array_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                    array_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                    array_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                    return true;
                }
            }
        }
        else {
            // hx.. - No more
//            Log.i(TAG, "(h x . .) - No more Records!");
            array_yellow.clear();
            array_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
            array_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
            return true;
        }

    }

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

//  ############################
//  ####  Show Old Graph:   ####
//  ############################

    private void analyseOldRecords() {
        double highest_temperature;
        GraphRecord   current_record;
        GraphRecord[] previous_records = new GraphRecord[6];
        ArrayList<GraphRecord> array_not_ignored = new ArrayList<>();;

        // Fill array with not ignored records:
        for (int i = 0; i < array_old_all.size(); i++) {
            if (!array_old_all.get(i).isIgnored()) {
                array_not_ignored.add(array_old_all.get(i));
            }
        }

        // Start analysing if there are at least 7 Records:
        if (array_not_ignored.size() > 6) {

            previous_records[0] = array_not_ignored.get(0);
            previous_records[1] = array_not_ignored.get(1);
            previous_records[2] = array_not_ignored.get(2);
            previous_records[3] = array_not_ignored.get(3);
            previous_records[4] = array_not_ignored.get(4);
            previous_records[5] = array_not_ignored.get(5);

            for (int i = 6; i < array_not_ignored.size(); i++) {
                // Get highest_temperature from last 6 Records:
                highest_temperature = Math.max(previous_records[0].getTemperature(), previous_records[1].getTemperature());
                highest_temperature = Math.max(previous_records[2].getTemperature(), highest_temperature);
                highest_temperature = Math.max(previous_records[3].getTemperature(), highest_temperature);
                highest_temperature = Math.max(previous_records[4].getTemperature(), highest_temperature);
                highest_temperature = Math.max(previous_records[5].getTemperature(), highest_temperature);

                // Check if current temperature is higher than last highest_temperature found:
                current_record = array_not_ignored.get(i);

                if (highest_temperature < current_record.getTemperature()) {
                    if (oldAnalysisDone(array_not_ignored, i, highest_temperature)) {
                        break;
                    }
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
        // First 6 temperatures cannot be analysed.
        else {
            array_old_yellow.clear();
        }
        // Set changes to arrays depending on the set yellow array.
        setOldAnalysisChanges();

    }

    private boolean oldAnalysisDone(ArrayList<GraphRecord> array_not_ignored, int i, double highest_temperature) {
        GraphRecord previous;
        GraphRecord first;
        GraphRecord second;
        GraphRecord third;
        GraphRecord fourth;

        previous = array_not_ignored.get(i - 1);
        first    = array_not_ignored.get(i);

        if (i++ < array_not_ignored.size() - 1) {
            // h?..
            second = array_not_ignored.get(i);
            if (highest_temperature < second.getTemperature()) {
                // hh..
                if (i++ < array_not_ignored.size() - 1) {
                    // hh?.
                    third = array_not_ignored.get(i);
                    if ( (highest_temperature * 5 + 1) / 5 <= third.getTemperature() ) {
                        // hhH. - Done
//                        Log.i(TAG, "(h h H .) - Done!");
                        array_old_yellow.clear();
                        array_old_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                        array_old_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                        array_old_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                        array_old_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                        return true;
                    }
                    else {
                        if (highest_temperature < third.getTemperature()) {
                            // hhh.
                            if (i++ < array_not_ignored.size() - 1) {
                                // hhh?
                                fourth = array_not_ignored.get(i);
                                if (highest_temperature < fourth.getTemperature()) {
                                    // hhhh - Done
//                                    Log.i(TAG, "(h h h h) - Done!");
                                    array_old_yellow.clear();
                                    array_old_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                                    array_old_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                                    array_old_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                                    array_old_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                                    array_old_yellow.add(new GraphRecord(fourth.getDay()  , fourth.getTemperature()));
                                    return true;
                                }
                                else {
                                    // hhhL - Reset
//                                    Log.i(TAG, "(h h h L) - Reset!");
                                    array_old_yellow.clear();
                                    return false;
                                }
                            }
                            else {
                                // hhhx - No More
//                                Log.i(TAG, "(h h h x) - No more Records!");
                                array_old_yellow.clear();
                                array_old_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                                array_old_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                                array_old_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                                array_old_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                                return true;
                            }
                        }
                        else {
                            // hhL.
                            if (i++ < array_not_ignored.size() - 1) {
                                // hhL? (? Has to be at least 0.20 higher)
                                fourth = array_not_ignored.get(i);
                                if ( (highest_temperature * 5 + 1) / 5 <= fourth.getTemperature() ) {
                                    // hhLH - Done
//                                    Log.i(TAG, "(h h L H) - Done!");
                                    array_old_yellow.clear();
                                    array_old_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                                    array_old_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                                    array_old_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                                    array_old_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                                    array_old_yellow.add(new GraphRecord(fourth.getDay()  , fourth.getTemperature()));
                                    return true;
                                }
                                else {
                                    // hhLL - Reset
//                                    Log.i(TAG, "(h h L L) - Reset! (Not 0.20 higher)");
                                    array_old_yellow.clear();
                                    return false;
                                }
                            }
                            else {
                                // hhL. - No More
//                                Log.i(TAG, "(h h L .) - No more Records!");
                                array_old_yellow.clear();
                                array_old_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                                array_old_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                                array_old_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                                array_old_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                                return true;
                            }
                        }
                    }
                }
                else {
                    // hhx. - No More
//                    Log.i(TAG, "(h h x .) - No more Records!");
                    array_old_yellow.clear();
                    array_old_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                    array_old_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                    array_old_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                    return true;
                }
            }
            else {
                // hL..
                if (i++ < array_not_ignored.size() - 1) {
                    // hL?. (? Has to be at least 0.20 higher)
                    third = array_not_ignored.get(i);
                    if ( (highest_temperature * 5 + 1) / 5 <= third.getTemperature() ) {
                        // hLH.
                        if (i++ < array_not_ignored.size() - 1) {
                            // hLH?
                            fourth = array_not_ignored.get(i);
                            if (highest_temperature < fourth.getTemperature()) {
                                // hLHh - Done
//                                Log.i(TAG, "(h L H h) - Done!");
                                array_old_yellow.clear();
                                array_old_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                                array_old_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                                array_old_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                                array_old_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                                array_old_yellow.add(new GraphRecord(fourth.getDay()  , fourth.getTemperature()));
                                return true;
                            }
                            else {
                                // hLHL - Reset
//                                Log.i(TAG, "(h L H L) - Reset!");
                                array_old_yellow.clear();
                                return false;
                            }
                        }
                        else {
                            // hLHx - No more
//                            Log.i(TAG, "(h L H x) - No more Records!");
                            array_old_yellow.clear();
                            array_old_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                            array_old_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                            array_old_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                            array_old_yellow.add(new GraphRecord(third.getDay()   , third.getTemperature()));
                            return true;
                        }
                    }
                    else {
                        // hLL. - Reset
//                        Log.i(TAG, "(h L L .) - Reset! (Not 0.20 higher)");
                        array_old_yellow.clear();
                        return false;
                    }
                }
                else {
                    // hLx. - No more
//                    Log.i(TAG, "(h L x .) - No more Records!");
                    array_old_yellow.clear();
                    array_old_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
                    array_old_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
                    array_old_yellow.add(new GraphRecord(second.getDay()  , second.getTemperature()));
                    return true;
                }
            }
        }
        else {
            // hx.. - No more
//            Log.i(TAG, "(h x . .) - No more Records!");
            array_old_yellow.clear();
            array_old_yellow.add(new GraphRecord(previous.getDay(), previous.getTemperature()));
            array_old_yellow.add(new GraphRecord(first.getDay()   , first.getTemperature()));
            return true;
        }

    }

    private void setOldAnalysisChanges() {
        double day;
        double temperature;

        array_old_red.clear();
        array_old_green.clear();

        for (int i = 0; i < array_old_all.size(); i++) {
            // Ignore hidden Records.
            if (!array_old_all.get(i).isIgnored()) {

                day         = array_old_all.get(i).getDay();
                temperature = array_old_all.get(i).getTemperature();

                if (array_old_yellow.isEmpty()) {
                    // Fill Red if Temperature Analysis is not done.
                    array_old_red.add(new GraphRecord(day, temperature));
                }
                else {
                    // Add temperatures till first Yellow to Red.
                    if (day <= array_old_yellow.get(0).getDay()) {
                        array_old_red.add(new GraphRecord(day, temperature));
                    }
                    // Add temperatures from last Yellow to Green.
                    if (array_old_yellow.get(array_old_yellow.size() - 1).getDay() <= day ) {
                        array_old_green.add(new GraphRecord(day, temperature));
                    }
                }

            }

        }

        // Sort arrays to prevent errors.
        array_old_red    = mergeSortArrayDay(array_old_red);
        array_old_yellow = mergeSortArrayDay(array_old_yellow);
        array_old_green  = mergeSortArrayDay(array_old_green);

        drawGraph();
    }

//  ############################
//  ####  Draw Graph:       ####
//  ############################

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
        series_tap_action.setTitle("Tab Listener");
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

        series_old_all = new LineGraphSeries<>();
        series_old_all.setTitle("Old Temperature");
        series_old_all.setDrawDataPoints(true);
        series_old_all.setDataPointsRadius(15);

        series_old_red = new LineGraphSeries<>();
        series_old_red.setTitle("Old Red");
        series_old_red.setColor(Color.parseColor("#44FF0000"));
        series_old_red.setDrawBackground(true);
        series_old_red.setBackgroundColor(Color.parseColor("#44FF0000"));

        series_old_yellow = new LineGraphSeries<>();
        series_old_yellow.setTitle("Old Yellow");
        series_old_yellow.setColor(Color.parseColor("#44FFFF00"));
        series_old_yellow.setDrawBackground(true);
        series_old_yellow.setBackgroundColor(Color.parseColor("#44FFFF00"));

        series_old_green = new LineGraphSeries<>();
        series_old_green.setTitle("Old Green");
        series_old_green.setColor(Color.parseColor("#4400FF00"));
        series_old_green.setDrawBackground(true);
        series_old_green.setBackgroundColor(Color.parseColor("#4400FF00"));
    }

    private void fillSeries() {
        double day;
        double temperature;

        // Old Cycle Data:
        array_old_all       = mergeSortArrayDay(array_old_all);
        array_old_red       = mergeSortArrayDay(array_old_red);
        array_old_yellow    = mergeSortArrayDay(array_old_yellow);
        array_old_green     = mergeSortArrayDay(array_old_green);

        for(int i = 0; i < array_old_red.size(); i++) {
            day         = array_old_red.get(i).getDay();
            temperature = array_old_red.get(i).getTemperature();
            series_old_red.appendData(new DataPoint(day, temperature), true, maxDataPoints);
        }
        for(int i = 0; i < array_old_yellow.size(); i++) {
            day         = array_old_yellow.get(i).getDay();
            temperature = array_old_yellow.get(i).getTemperature();
            series_old_yellow.appendData(new DataPoint(day, temperature), true, maxDataPoints);
        }
        for(int i = 0; i < array_old_green.size(); i++) {
            day         = array_old_green.get(i).getDay();
            temperature = array_old_green.get(i).getTemperature();
            series_old_green.appendData(new DataPoint(day, temperature), true, maxDataPoints);
        }
        for (int i = 0; i < array_old_all.size(); i++) {
            day         = array_old_all.get(i).getDay();
            temperature = array_old_all.get(i).getTemperature();
            series_old_all.appendData(new DataPoint(day, temperature), true, maxDataPoints);
            // Connection between old and current:
            if (i == array_old_all.size() - 1) {
                series_red.appendData(new DataPoint(day, temperature), true, maxDataPoints);
                series_all.appendData(new DataPoint(day, temperature), true, maxDataPoints);
            }
        }

        // Current Cycle Data:
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

        // Array:
        array_guideline.clear();
        if (!array_yellow.isEmpty()) {
//            Log.i(TAG, "Adding Guideline.");
            temperature = array_yellow.get(0).getTemperature();
            if (!array_old_all.isEmpty()) {
                day = array_old_all.get(0).getDay();
            }
            else {
                day = array_all    .get(0).getDay();
            }
            for (double i = day - 0.50; i < array_all.get(array_all.size() - 1).getDay() + minDaysShown + 0.50; i = i + 0.50) {
                array_guideline.add(new GraphRecord(i, temperature));
            }
        }
        else {
//            Log.i(TAG, "Adding Padding.");
            // Front:
            if (!array_old_all.isEmpty()) {
                day = array_old_all.get(0).getDay() - 0.50;
                array_guideline.add(new GraphRecord(day, 36.50));
            }
            else {
                day = - 0.50;
                array_guideline.add(new GraphRecord(day, 36.50));
            }
            // Back:
            if (!array_all.isEmpty()) {
                day = array_all.get(array_all.size() - 1).getDay() + minDaysShown + 0.50;
                array_guideline.add(new GraphRecord(day, 36.50));
            }
            else {
                day = minDaysShown + 0.50;
                array_guideline.add(new GraphRecord(day, 36.50));
            }

        }

        // Series:
        array_guideline = mergeSortArrayDay(array_guideline);
        for (int i = 0; i < array_guideline.size(); i++) {
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



        if(!series_old_red.isEmpty()) {
            graph.addSeries(series_old_red);
        }
        if(!series_old_yellow.isEmpty()) {
            graph.addSeries(series_old_yellow);
        }
        if(!series_old_green.isEmpty()) {
            graph.addSeries(series_old_green);
        }
        if(!series_old_all.isEmpty()) {
            graph.addSeries(series_old_all);
        }

    }

    private void setViewport() {
        ArrayList<GraphRecord> boundArray;
        double highestX;
        double highestY;

        double upperBoundX;
        double lowerBoundX;
        int numVerticalLines;

        double upperBoundY;
        double lowerBoundY;
        int numHorizontalLines;

                        /* Set manual Viewport for X. */

        numVerticalLines = maxDaysShown + 1;

        boundArray = mergeSortArrayDay(array_all);

        if(boundArray.isEmpty()) {
//            Log.i(TAG, "No Days in current Cycle.");
            lowerBoundX      = minDaysShown - maxDaysShown  + 1 - 0.50;
            upperBoundX      = minDaysShown                     + 0.50;
        }
        else {
            highestX = boundArray.get(boundArray.size() - 1).getDay();
//            Log.i(TAG, "Showing minDayShown into future from latest current day.");
            upperBoundX      = highestX + minDaysShown                    + 0.50;
            lowerBoundX      = highestX + minDaysShown - maxDaysShown + 1 - 0.50;
        }

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMaxX(upperBoundX);
        graph.getViewport().setMinX(lowerBoundX);
//        graph.getGridLabelRenderer().setHorizontalAxisTitle("Day");
        graph.getGridLabelRenderer().setNumHorizontalLabels(numVerticalLines);

                        /* Set manual Viewport for Y. */

        numHorizontalLines = (int) ( maxTemperatureRangeShown / 0.05 ) + 1;
        boundArray = array_all;
        boundArray.addAll(array_old_all);
        boundArray = mergeSortArrayTemperature(boundArray);

        if(boundArray.isEmpty()) {
//            Log.i(TAG, "No Temperature in current Cycle.");
            upperBoundY = 37.00                           ;
            lowerBoundY = 37.00 - maxTemperatureRangeShown;
        }
        else {
            highestY = boundArray.get(boundArray.size() - 1).getTemperature();

            if (highestY < 37.00) {
//                Log.i(TAG, "Temperature lower  than 37.00 in current Cycle.");
                upperBoundY = 37.00                           ;
                lowerBoundY = 37.00 - maxTemperatureRangeShown;
            }
            else {
//                Log.i(TAG, "Temperature higher than 37.00 in current Cycle.");
                upperBoundY = highestY                           ;
                lowerBoundY = highestY - maxTemperatureRangeShown;
            }
        }

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMaxY(upperBoundY);
        graph.getViewport().setMinY(lowerBoundY);
//        graph.getGridLabelRenderer().setVerticalAxisTitle("Temperature");
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

//                        tempTestData();

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
        alarm_time.set(Calendar.SECOND, 0);
        // App opened after 6:00, show Notification tomorrow again.
        if(alarm_time.getTimeInMillis() < Calendar.getInstance().getTimeInMillis()) {
            alarm_time.add(Calendar.DAY_OF_MONTH, 1);
        }

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
        cal.add(Calendar.DATE, -30);

        int saved_current_cycle = current_cycle;

        current_cycle = 0;
        db_helper.deleteCycleRecords(current_cycle);
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
        db_helper.updateCycleDate(current_cycle, cal.getTimeInMillis());
        current_cycle = saved_current_cycle;

        drawGraph();

    }

    private void tempTestDatabase() {
        Calendar cycle_start = Calendar.getInstance();
        cycle_start.setTimeInMillis(db_helper.getCycleDate(1));

        showToastMessage(
                "Record(0) Count: " + db_helper.getRecordCount(0) + "\n" +
                "Record(1) Count: " + db_helper.getRecordCount(1) + "\n" +
                "Record(2) Count: " + db_helper.getRecordCount(2) + "\n" +
                "Record(3) Count: " + db_helper.getRecordCount(3) + "\n"
        );


    }

}
