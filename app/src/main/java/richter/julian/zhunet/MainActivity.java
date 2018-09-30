package richter.julian.zhunet;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupGraphButton();

        // TODO: Settings for start Screen (here start graph)
        Intent intent = new Intent(MainActivity.this, GraphActivity.class);
        startActivity(intent);
    }

    private void setupGraphButton() {
        Button btnGraph = findViewById(R.id.bGraphMenu);
        btnGraph.setOnClickListener(
            // Inner Class.
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = GraphActivity.makeIntent(MainActivity.this);
                    startActivity(intent);
                }
            }
        );
    }
}
