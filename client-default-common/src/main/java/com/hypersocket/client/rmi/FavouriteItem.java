package com.hypersocket.client.rmi;

public interface FavouriteItem {

	Long getId();

	String getUid();

	Boolean getFavourite();
	
	void setFavourite(Boolean favourite);

	void setId(Long id);
	
	void setUid(String uid);
}
