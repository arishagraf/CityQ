package com.release.cityq;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.InflaterOutputStream;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Button confirm_Country;
    private TextView  km_location, city_find, cities_found, select_tv;
    private RequestQueue mQueue;
    private String Intent_City;
    private String zurich, berlin, amsterdam, oslo, vienna, madrid, paris, rome, london;

    private final static int LOCATION_REQUEST_CODE = 23;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        mQueue = Volley.newRequestQueue(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        city_find = findViewById(R.id.city_find);
        km_location = findViewById(R.id.km_location);
        confirm_Country = findViewById(R.id.place_flag);
        cities_found = findViewById(R.id.cities_found);
        select_tv = findViewById(R.id.select_tv);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_REQUEST_CODE);
        }
        getCapitalCitiesJSON();
        onClick();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        }

        //set Map style without Labels and streets' name
        try {

            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.mapstyle));
            if(!success){
                Log.e("MapActivity", "Style parsing failed");
            }
        } catch (Resources.NotFoundException e){
            Log.e("MapActivity", "Can't find style. Error", e);
        }

        create();
    }

    private void create(){
        //here I set current user loction to begin from
        mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
            @Override
            public void onMyLocationChange(Location location) {
                LatLng ltlng=new LatLng(location.getLatitude(),location.getLongitude());
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                        ltlng, 16f);
                mMap.animateCamera(cameraUpdate);
            }
        });
        //Here map-click handler
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(latLng);

                markerOptions.title(getAddress(latLng));
                mMap.clear();
                CameraUpdate location = CameraUpdateFactory.newLatLngZoom(
                        latLng, 15);
                mMap.animateCamera(location);
                mMap.addMarker(markerOptions);
            }
        });
    }

    private String getAddress(LatLng latLng){

        Geocoder geocoder;
        List<Address> addresses;
        geocoder = new Geocoder(this, Locale.getDefault());
        try {
            addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            String city = addresses.get(0).getLocality(); //I get only cities name to compare with JSON
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            Fragment prev = getFragmentManager().findFragmentByTag("dialog");
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);
            DialogFragment dialogFragment = new ConfirmAddress();

            Bundle args = new Bundle();
            args.putDouble("lat", latLng.latitude);
            args.putDouble("long", latLng.longitude);
            args.putString("City", city);
            dialogFragment.setArguments(args);
            dialogFragment.show(ft, "dialog");
            return city;
        } catch (IOException e) {
            e.printStackTrace();
            return "No city Found";
        }
    }

    public void getCapitalCitiesJSON(){
        /*
        I've decided to host our JSON file,so that i can access them through Volley library
        Also I have made this game with following logic:
        just fetching data from JSON and compare equivalence with selected city on the map,
        if they are equal textview will be updated with next city to find.
         **/

        String url = "https://jsonkeeper.com/b/ROXB";
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onResponse(JSONObject response) {
                        try{
                            JSONArray jsonArray = response.getJSONArray("capitalCities");
                            if(jsonArray.length() > 0) {
                                JSONObject Object_Zurich = jsonArray.getJSONObject(0);
                                JSONObject Object_Paris = jsonArray.getJSONObject(1);
                                JSONObject Object_Madrid = jsonArray.getJSONObject(2);
                                JSONObject Object_London = jsonArray.getJSONObject(3);
                                JSONObject Object_Berlin = jsonArray.getJSONObject(4);
                                JSONObject Object_Amsterdam = jsonArray.getJSONObject(5);
                                JSONObject Object_Rome = jsonArray.getJSONObject(6);
                                JSONObject Object_Oslo = jsonArray.getJSONObject(7);
                                JSONObject Object_Vienna = jsonArray.getJSONObject(8);

                                zurich = Object_Zurich.getString("capitalCity");
                                 paris = Object_Paris.getString("capitalCity");
                                 madrid = Object_Madrid.getString("capitalCity");
                                 london = Object_London.getString("capitalCity");
                                 berlin = Object_Berlin.getString("capitalCity");
                                 amsterdam = Object_Amsterdam.getString("capitalCity");
                                 rome = Object_Rome.getString("capitalCity");
                                 oslo = Object_Oslo.getString("capitalCity");
                                 vienna = Object_Vienna.getString("capitalCity"); //final string

                            }
                        } catch(JSONException e){
                            e.printStackTrace();
                        }

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        });
        mQueue.add(request);
    }

    private void onClick() {
        confirm_Country.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {

                Intent_City = getIntent().getStringExtra("city");
                assert Intent_City != null;

                if (zurich.equals("Zurich") && Intent_City.equals("Zürich")) {
                    Toast.makeText(MapActivity.this, "Zurich Equal ", Toast.LENGTH_LONG).show();
                    km_location.setText("1350 km left");
                    cities_found.setText("1 city placed");
                    city_find.setText("Paris");
                }

                if (paris.equals(Intent_City)) {
                    Toast.makeText(MapActivity.this, "Paris Equal", Toast.LENGTH_LONG).show();
                    km_location.setText("1200 km left");
                    cities_found.setText("2 cities placed");
                    city_find.setText("Madrid");
                }

                if (madrid.equals(Intent_City)) {
                    Toast.makeText(MapActivity.this, "Madrid Equal", Toast.LENGTH_LONG).show();
                    km_location.setText("1050 km left");
                    cities_found.setText("3 cities placed");
                    city_find.setText("London");
                }


                if (london.equals(Intent_City)) {
                    Toast.makeText(MapActivity.this, "London Equal", Toast.LENGTH_LONG).show();
                    km_location.setText("900 km left");
                    cities_found.setText("4 cities placed");
                    city_find.setText("Berlin");
                }


                if (berlin.equals(Intent_City)) {
                    Toast.makeText(MapActivity.this, "Berlin Equal", Toast.LENGTH_LONG).show();
                    km_location.setText("750 km left");
                    cities_found.setText("5 cities placed");
                    city_find.setText("Amsterdam");
                }


                if (amsterdam.equals(Intent_City)) {
                    Toast.makeText(MapActivity.this, "Amsterdam Equal", Toast.LENGTH_LONG).show();
                    km_location.setText("600 km left");
                    cities_found.setText("6 cities placed");
                    city_find.setText("Rome");
                }


                if (rome.equals(Intent_City)) {
                    Toast.makeText(MapActivity.this, "Rome Equal", Toast.LENGTH_LONG).show();
                    km_location.setText("450 km left");
                    cities_found.setText("7 cities placed");
                    city_find.setText("Oslo");
                }


                if (oslo.equals(Intent_City)) {
                    Toast.makeText(MapActivity.this, "Oslo Equal", Toast.LENGTH_LONG).show();
                    km_location.setText("300 km left");
                    cities_found.setText("8 cities placed");
                    city_find.setText("Vienna");
                }

                if (vienna.equals("Vienna") && Intent_City.equals("Wien")) {
                    Toast.makeText(MapActivity.this, "Vienna Equal", Toast.LENGTH_LONG).show();
                    km_location.setText("150 km left");
                    cities_found.setText("9 cities placed");
                    city_find.setText("Zürich");

                    // to not repeat Zurich, I set it after completing vienna
                    if (zurich.equals("Zurich")) {
                        Toast.makeText(MapActivity.this, "Zurich Equal", Toast.LENGTH_LONG).show();
                        km_location.setText("YOU WON");
                        cities_found.setText("All countries are found");
                        select_tv.setText("");
                        city_find.setText("GAME OVER");
                    }
                }
            }
        });
    }
}

