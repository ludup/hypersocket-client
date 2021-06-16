package com.logonbox.vpn.client.service;

import com.logonbox.vpn.common.client.Prompt;

public class PromptImpl implements Prompt {

	private String title;
	private String text;
	private String[] titleParameters;
	private String[] textParameters;
	private String[] choices;

	public PromptImpl() {
	}

	protected PromptImpl(Prompt base) {
		title = base.getTitle();
		text = base.getText();
		titleParameters = base.getTitleParameters();
		textParameters = base.getTextParameters();
		choices = base.getChoices();
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String[] getTitleParameters() {
		return titleParameters;
	}

	public void setTitleParamters(String[] titleParameters) {
		this.titleParameters = titleParameters;
	}

	public String[] getTextParameters() {
		return textParameters;
	}

	public void setTextParameters(String[] textParameters) {
		this.textParameters = textParameters;
	}

	public String[] getChoices() {
		return choices;
	}

	public void setChoices(String[] choices) {
		this.choices = choices;
	}

}
