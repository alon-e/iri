package com.iota.iri.service.dto;

public class GetAlphaResponse extends AbstractResponse {

	private double alpha;

	public static AbstractResponse create(double alpha) {
		GetAlphaResponse res = new GetAlphaResponse();
		res.alpha = alpha;
		return res;
	}
	
	public double getAlpha() {
		return alpha;
	}

}
