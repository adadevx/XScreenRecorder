package com.adadev.xrecorder.adapter;

import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.adadev.xrecorder.R;

import java.util.ArrayList;

public class FAQAdapter extends RecyclerView.Adapter<FAQAdapter.SimpleViewHolder> {
    private ArrayList<FAQModel> FAQs;

    public FAQAdapter(ArrayList<FAQModel> FAQs) {
        this.FAQs = FAQs;
    }

    private RecyclerView recyclerView;

    @SuppressWarnings("deprecation")
    private static Spanned fromHtml(String source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(source);
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        this.recyclerView = recyclerView;
    }

    @NonNull
    @Override
    public FAQAdapter.SimpleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.faq_content, parent, false);
        return new SimpleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FAQAdapter.SimpleViewHolder holder, int position) {
        FAQModel faq = FAQs.get(position);
        holder.question.setText(faq.getQuestion());
        holder.answer.setText(fromHtml(faq.getAnswer()));
        holder.questionView.setOnClickListener(v -> {
            boolean visible = holder.answerView.getVisibility() == View.VISIBLE;
            holder.answerView.setVisibility(
                    visible ? View.GONE : View.VISIBLE
            );
            int angle = visible ? 0 : 90;
            holder.btn.animate().rotation(angle).setDuration(200);

            TransitionManager.beginDelayedTransition(recyclerView);
        });
    }

    @Override
    public int getItemCount() {
        return FAQs.size();
    }

    static class SimpleViewHolder extends RecyclerView.ViewHolder {
        TextView question;
        TextView answer;
        ImageView btn;
        RelativeLayout questionView;
        LinearLayout answerView;

        SimpleViewHolder(@NonNull View itemView) {
            super(itemView);
            question = itemView.findViewById(R.id.faq_title);
            answer = itemView.findViewById(R.id.faq_answer);
            btn = itemView.findViewById(R.id.expand_btn);
            questionView = itemView.findViewById(R.id.faq_question_view);
            answerView = itemView.findViewById(R.id.faq_answer_view);
        }
    }
}
