package com.adadev.xrecorder.adapter;

public class FAQModel implements Comparable<FAQModel> {
    private String question;
    private String answer;
    private int weight;

    public FAQModel(String question, String answer, int Weight) {
        this.question = question;
        this.answer = answer;
        this.weight = Weight;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    @Override
    public int compareTo(FAQModel o) {
        return Integer.compare(this.getWeight(), o.getWeight());
    }
}
