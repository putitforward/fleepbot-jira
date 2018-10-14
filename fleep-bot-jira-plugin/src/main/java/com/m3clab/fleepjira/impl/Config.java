package com.m3clab.fleepjira.impl;

import org.codehaus.jackson.annotate.JsonAutoDetect;

/**
 *
 * @author SDNj
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE)
public final class Config {
	
	private String login;
	private String password;
	private String keys;

	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		this.login = login;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getKeys() {
		return keys;
	}

	public void setKeys(String keys) {
		this.keys = keys;
	}

	@Override
	public String toString() {
		return "Config{" + "login=" + login + ", password=" + (password != null) + ", keys=" + keys + '}';
	}
	
}
