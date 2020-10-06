package me.egaetan.mytwitt;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

public class MyClient {

	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		String host = "http://localhost:8081";
		
		CookieManager cookieManager = new CookieManager();
		cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
		
		HttpClient client = HttpClient.newBuilder()
				.cookieHandler(cookieManager)
				.build();
		
		HttpRequest reqLogin = HttpRequest.newBuilder()
				.GET()
				.uri(URI.create(host + "/login/egaetan"))
				.build();
		
		HttpResponse<String> sendLogin = client.send(reqLogin, BodyHandlers.ofString());
		System.out.println(sendLogin.body());

		HttpRequest reqTimeline = HttpRequest.newBuilder()
				.GET()
				.uri(URI.create(host + "/timeline"))
				.build();
		
		HttpResponse<String> sendTimeline = client.send(reqTimeline, BodyHandlers.ofString());
		System.out.println(sendTimeline.body());
	}
	
}
