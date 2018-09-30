package richter.julian.zhunet;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class GraphTemperatureItemAdapter extends ArrayAdapter<String> {

    private Context adapterContext;
    private int adapterResource;

    public GraphTemperatureItemAdapter(Context context, int resource, ArrayList<String> objects) {
        super(context, resource, objects);
        adapterContext = context;
        adapterResource = resource;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        String temperature;
        LayoutInflater inflater;
        TextView tvTemperatureItem;

        temperature = getItem(position);

        inflater = LayoutInflater.from(adapterContext);
        convertView = inflater.inflate(adapterResource, parent, false);

        tvTemperatureItem = convertView.findViewById(R.id.tv_temperature_item);
        tvTemperatureItem.setText(temperature);

        return convertView;
    }
}
