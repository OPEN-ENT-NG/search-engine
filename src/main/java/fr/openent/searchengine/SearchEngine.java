package fr.openent.searchengine;

import fr.openent.searchengine.controllers.SearchEngineController;
import org.entcore.common.http.BaseServer;

public class SearchEngine extends BaseServer {

	@Override
	public void start() {
		super.start();

		addController(new SearchEngineController(config.getInteger("max-sec-time-allowed", 4),
				config.getInteger("paging-size-per-collection", 10), config.getInteger("search-word-min-size", 4)));
	}

}
