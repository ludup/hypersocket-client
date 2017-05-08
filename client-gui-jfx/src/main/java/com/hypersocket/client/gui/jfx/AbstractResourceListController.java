package com.hypersocket.client.gui.jfx;

import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.rmi.FavouriteItem;
import com.hypersocket.client.rmi.FavouriteItemImpl;
import com.hypersocket.client.rmi.FavouriteItemService;
import com.hypersocket.client.rmi.Resource;
import com.hypersocket.client.rmi.Resource.Type;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public abstract class AbstractResourceListController extends AbstractController {
	
	static Logger LOG = LoggerFactory.getLogger(AbstractResourceListController.class);
	
	@FXML
	protected VBox resourceListItems;

	protected Set<String> resourceUidCache = new HashSet<>();
	
	@Override
	protected void onInitialize() {
		resourceListItems.focusTraversableProperty().set(true);
	}
	
	public void clearList() {
		resourceListItems.getChildren().clear();
		resourceUidCache.clear();
	}
	
	public abstract void setResources(Map<ResourceGroupKey, ResourceGroupList> icons);

	public void populateResource(Map<ResourceGroupKey, ResourceGroupList> icons,Type filter) {
		if(icons == null) {
			return;
		}
		Set<ResourceGroupKey> resourceGroupKeys = icons.keySet();
		for (ResourceGroupKey resourceGroupKey : resourceGroupKeys) {
			if(resourceGroupKey.getType() != filter) {
				continue;
			}
			ResourceGroupList groupList = icons.get(resourceGroupKey);
			List<ResourceItem> items = groupList.getItems();
			
			for (ResourceItem item : items) {
				if(resourceUidCache.contains(item.getResource().getUid())) {
					continue;
				}
				
				HBox hb = new HBox();
				hb.getStyleClass().add("item");

				// Icon
				ToggleButton favourite = new ToggleButton();
				favourite.setText(resources.getString("favourite.checked.icon"));
				favourite.setUserData(item.getResource());
				favourite.getStyleClass().add("icon");
				favourite.getStyleClass().add("favourite");
				if(item.getResource().getFavourite()) {
					favourite.setSelected(true);
				}
				hb.getChildren().add(favourite);
				
				favourite.setOnMousePressed(new EventHandler<Event>() {

					@Override
					public void handle(Event event) {
						try{
							FavouriteItemService favouriteItemService = context.getBridge().getFavouriteItemService();
							ToggleButton favourite = (ToggleButton) event.getSource();
							Resource resource = (Resource) favourite.getUserData();
							FavouriteItem favouriteItem = favouriteItemService.getFavouriteItem(resource.getUid());
							if(favouriteItem == null) {
								favouriteItem = new FavouriteItemImpl();
								favouriteItem.setUid(resource.getUid());
							}
							if(favourite.isSelected()) {
								//remove from the favourite list	
								resource.setFavourite(false);
								favouriteItem.setFavourite(false);
							} else {
								//add to the favourite list
								resource.setFavourite(true);
								favouriteItem.setFavourite(true);
							}
							Dock.getInstance().rebuildIcons();
							favouriteItemService.saveOrUpdate(favouriteItem);
						} catch (RemoteException e) {
							throw new IllegalStateException(e.getMessage(), e);
						}
					}
				});
				 
				// Text
				Label name = new Label();
				name.setText(item.getResource().getName());
				name.setTooltip(new Tooltip(item.getResource().getName()));
				hb.getChildren().add(name);
				
				//Icon
				IconButton icon = new IconButton(resources, item, context, groupList);
				hb.getChildren().add(icon);
				
				resourceUidCache.add(item.getResource().getUid());
				resourceListItems.getChildren().add(hb);
				
			}
		}
		
	}
}
