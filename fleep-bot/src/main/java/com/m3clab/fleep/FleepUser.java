package com.m3clab.fleep;

/**
 *
 * @author SDNj
 */
public class FleepUser {

	private final String accountId;

	public FleepUser(String accountId) {
		this.accountId = accountId;
	}

	public String getAccountId() {
		return accountId;
	}

	public String getEmail() {
		throw new UnsupportedOperationException("Need implement it.");
	}

}
