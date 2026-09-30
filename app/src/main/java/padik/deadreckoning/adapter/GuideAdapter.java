package padik.deadreckoning.adapter;

import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import padik.deadreckoning.R;
import padik.deadreckoning.model.GuideItem;

public class GuideAdapter extends RecyclerView.Adapter<GuideAdapter.GuideViewHolder> {

    private List<GuideItem> items;

    public GuideAdapter(List<GuideItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public GuideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_guide, parent, false);
        return new GuideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GuideViewHolder holder, int position) {
        GuideItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateItems(List<GuideItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    static class GuideViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView title;
        private final TextView content;

        GuideViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.guideIcon);
            title = itemView.findViewById(R.id.guideTitle);
            content = itemView.findViewById(R.id.guideContent);
        }

        void bind(GuideItem item) {
            icon.setImageResource(item.getIconRes());
            title.setText(item.getTitle());
            content.setText(item.getContent());
            Linkify.addLinks(content, Linkify.WEB_URLS);
            content.setLinkTextColor(itemView.getContext().getColor(R.color.colorAccent));
        }
    }
}
