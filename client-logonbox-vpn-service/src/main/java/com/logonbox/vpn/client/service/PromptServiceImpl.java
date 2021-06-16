package com.logonbox.vpn.client.service;

import java.util.Stack;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.freedesktop.dbus.exceptions.DBusException;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.Prompt;
import com.logonbox.vpn.common.client.PromptService;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNPrompt;

public class PromptServiceImpl implements PromptService {

	private Stack<Prompt> prompts = new Stack<>();
	private LocalContext cctx;

	public PromptServiceImpl(LocalContext cctx) {
		this.cctx = cctx;
	}

	@Override
	public Prompt peek() {
		synchronized (prompts) {
			return prompts.peek();
		}
	}

	@Override
	public Prompt pop() {
		synchronized (prompts) {
			return prompts.pop();
		}
	}

	@Override
	public void prompt(String title, String[] titleParameters, String text, String[] textParameters,
			String... choices) throws InterruptedException {

		PromptImpl pimpl = new PromptImpl();
		synchronized (prompts) {

			pimpl.setTitle(title);
			pimpl.setTitleParamters(titleParameters);
			pimpl.setText(text);
			pimpl.setTextParameters(textParameters);
			pimpl.setChoices(choices);

			prompts.push(pimpl);
			
			PrompWrapper w = new PrompWrapper(pimpl);
			
			try {
				cctx.getConnection().exportObject(w);
			} catch (DBusException e1) {
				throw new IllegalStateException("Failed to export prompt.", e1);
			}

			/*
			 * Tell the front end about the certificate exception, and wait for up to 2
			 * minutes for the user to respond.
			 */
			try {
				cctx.sendMessage(new VPN.Prompt("/com/logonbox/vpn"));
			} catch (DBusException e) {
				throw new IllegalStateException("Failed to send event.", e);
			}

			Semaphore sem = new Semaphore(2);

			sem.tryAcquire(2, TimeUnit.MINUTES);
		}
	}
	
	class PrompWrapper extends PromptImpl implements VPNPrompt  {
		
		PrompWrapper(Prompt base) {
			super(base);
		}

		@Override
		public String getObjectPath() {
			return "/com/logonbox/vpn/prompt";
		}

		@Override
		public void choice(String choice) {
			// TODO Auto-generated method stub
			
		}
		
	}

}
