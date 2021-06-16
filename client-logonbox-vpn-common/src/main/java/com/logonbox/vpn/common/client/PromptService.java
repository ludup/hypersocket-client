package com.logonbox.vpn.common.client;

public interface PromptService {
	
	void prompt(String title, String[] titleParameters, String text, String[] textParameters, String... choices) throws InterruptedException;
	
	Prompt peek();
	
	Prompt pop();
}
