package g.jom.rejod;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private final List<Uri> videoUris;
    private final OnItemClickListener listener;
    private Context context;

    public interface OnItemClickListener {
        void onItemClick(Uri videoUri);
    }

    public VideoAdapter(List<Uri> videoUris, OnItemClickListener listener) {
        this.videoUris = videoUris;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.video_item, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Uri videoUri = videoUris.get(position);
        holder.bind(videoUri, listener);

        holder.videoTitle.setText("Recording " + (position + 1));

        // **FIX: Use the modern method for generating thumbnails**
        Bitmap thumbnail = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10 (API 29) and above
                thumbnail = context.getContentResolver().loadThumbnail(
                        videoUri, new Size(120, 80), null);
            } else {
                // For older versions
                // This method is deprecated but required for backward compatibility
                thumbnail = MediaStore.Video.Thumbnails.getThumbnail(
                        context.getContentResolver(),
                        Long.parseLong(videoUri.getLastPathSegment()),
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (thumbnail != null) {
            holder.videoThumbnail.setImageBitmap(thumbnail);
        } else {
            // Set a placeholder if thumbnail generation fails
            holder.videoThumbnail.setImageResource(R.mipmap.ic_launcher);
        }
    }

    @Override
    public int getItemCount() {
        return videoUris.size();
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView videoThumbnail;
        TextView videoTitle;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            videoThumbnail = itemView.findViewById(R.id.videoThumbnail);
            videoTitle = itemView.findViewById(R.id.videoTitle);
        }

        public void bind(final Uri videoUri, final OnItemClickListener listener) {
            itemView.setOnClickListener(v -> listener.onItemClick(videoUri));
        }
    }
}
