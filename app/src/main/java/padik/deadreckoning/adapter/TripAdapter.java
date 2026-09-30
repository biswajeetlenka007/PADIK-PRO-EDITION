package padik.deadreckoning.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import padik.deadreckoning.R;
import padik.deadreckoning.model.Trip;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private List<Trip> trips;
    private final OnTripClickListener listener;
    
    public interface OnTripClickListener {
        void onTripClick(Trip trip);
        void onTripLongClick(Trip trip);
    }

    public TripAdapter(List<Trip> trips, OnTripClickListener listener) {
        this.trips = trips;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);
        holder.bind(trip);
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    public void updateTrips(List<Trip> trips) {
        this.trips = trips;
        notifyDataSetChanged();
    }

    class TripViewHolder extends RecyclerView.ViewHolder {
        private final TextView textName;
        private final TextView textDate;
        private final TextView textDistance;
        private final TextView textDuration;
        private final TextView textSteps;
        private final ImageButton buttonExport;

        TripViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.textTripName);
            textDate = itemView.findViewById(R.id.textTripDate);
            textDistance = itemView.findViewById(R.id.textTripDistance);
            textDuration = itemView.findViewById(R.id.textTripDuration);
            textSteps = itemView.findViewById(R.id.textTripSteps);
            buttonExport = itemView.findViewById(R.id.buttonExport);
        }

        void bind(Trip trip) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
            
            String name = trip.getName();
            textName.setText(name != null ? name : itemView.getContext().getString(R.string.trip));
            textDate.setText(sdf.format(new Date(trip.getStartTime())));
            textDistance.setText(String.format(Locale.US, "%.2f km", trip.getTotalDistance() / 1000));
            textDuration.setText(trip.getFormattedDuration());
            textSteps.setText(String.valueOf(trip.getTotalSteps()));

            itemView.setOnClickListener(v -> listener.onTripClick(trip));
            itemView.setOnLongClickListener(v -> {
                listener.onTripLongClick(trip);
                return true;
            });

            buttonExport.setOnClickListener(v -> listener.onTripLongClick(trip));
        }
    }
}
