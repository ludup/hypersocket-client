package com.logonbox.vpn.common.client;

public interface Prompt {

	String getTitle();
	
	String[] getTitleParameters();

	String getText();
	
	String[] getTextParameters();

	String[] getChoices();
}
