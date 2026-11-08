package com.pca.lesotho.citizen.ui.onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pca.lesotho.citizen.R;

public class OnboardingPagerAdapter extends RecyclerView.Adapter<OnboardingPagerAdapter.PageHolder> {

    public static final int PAGE_COUNT = 3;

    private static final int[] TITLES = {
            R.string.onboarding_title_1, R.string.onboarding_title_2, R.string.onboarding_title_3
    };
    private static final int[] BODIES = {
            R.string.onboarding_body_1, R.string.onboarding_body_2, R.string.onboarding_body_3
    };

    @NonNull
    @Override
    public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_page, parent, false);
        return new PageHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PageHolder holder, int position) {
        holder.title.setText(TITLES[position]);
        holder.body.setText(BODIES[position]);
    }

    @Override
    public int getItemCount() { return PAGE_COUNT; }

    static class PageHolder extends RecyclerView.ViewHolder {
        TextView title, body;
        PageHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.pageTitle);
            body = itemView.findViewById(R.id.pageBody);
        }
    }
}
