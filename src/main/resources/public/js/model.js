var descriptionMaxSize = 140;

function SearchField() {
	this.words = "";
	this.descriptionMaxSize = descriptionMaxSize;
	this.init = function() {
		model.searchs.all = [];
		model.searchs.page = 0;
		model.searchs.lastPage = false;
	}
}

function Search() {
	this.expanded = false;

	if (this.description) {
		var descWithoutHref = this.description.replace(/href="([^"]+)/g, '');
	   if (descWithoutHref.length >= descriptionMaxSize) {
		   this.previewDesc = '<p>' + $('<div>' + this.description + '</div>').text().substring(0, descriptionMaxSize) + '...' + '</p>';
	   } else {
		   this.previewDesc = this.description;
	   }
	} else {
		this.previewDesc = this.description;
	}
}

function SearchType(){
	this.apply = function() {
		model.searchs.all = [];
		model.searchs.page = 0;
		if (model.searchTypes.selection().length > 0) {
			model.searchTypes.noFilter = false;
		}
		if (window.sessionStorage) {
			var storedSearchFilter = JSON.parse(sessionStorage.getItem("storedSearchFilter"));

			if (storedSearchFilter && storedSearchFilter.length > 0) {
				if (this.selected) {
					storedSearchFilter.push(this.data);
					sessionStorage.setItem("storedSearchFilter", JSON.stringify(storedSearchFilter));
				} else {
					for (var i = 0; i < storedSearchFilter.length; i++) {
						var currentFilter = storedSearchFilter[i];
						if (this.data === currentFilter) {
							storedSearchFilter.splice(i, 1);
							break;
						}
					}
					sessionStorage.setItem("storedSearchFilter", JSON.stringify(storedSearchFilter));
				}
			}
		}
	}
}

model.parseError = function(e) {
	var error = {};
	try {
		error = JSON.parse(e.responseText);
	}
	catch (err) {
		error.error = "search.engine.error.unknown";
	}
	error.status = e.status;

	return error;
};

model.build = function (){
	this.makeModels([Search, SearchType, SearchField]);

	this.searchField = new SearchField();

	this.collection(Search, {
		page: 0,
		lastPage: false,
		loading: false,
		sync: function(paginate, cb, cbe){
			var that = this;

			if (that.loading)
				return;
			
			if (paginate && that.lastPage) {
				cb(false, false);
				return;
			}

			var types = model.searchTypes.selection();
			if(model.searchTypes.noFilter){
				types = model.searchTypes.all;
			}

			var searchText = model.searchField.words;
			if(!types.length || !searchText || searchText == ""){
				return;
			}

			var params = { filter: _.map(types, function(type){
				return type.data;
			})};

			params.currentPage = that.page;
			params.searchText = searchText;

			that.loading = true;

			http().postJson('/searchengine', params).done(function(resultssearch){
				if(resultssearch.results.length > 0){
					that.addRange(resultssearch.results);
					that.page++;
					if (resultssearch.hasMoreResult === false) that.lastPage = true;
				} else {
					that.lastPage = true;
				}
				if(typeof cb === 'function'){
					cb(resultssearch.status, resultssearch.hasMoreResult);
				}
				that.loading = false;
			}).error(function(e){
				that.loading = false;
				if(typeof cbe === 'function'){
					cbe(model.parseError(e));
				}
			}).bind(this);
		},
		clear: function() {
			this.all = [];
		}
	});

	this.collection(SearchType, {
		sync: function(cb){
			http().get('/searchengine/types').done(function(resulttypes){
				this.load(resulttypes);

				var that = this;

				if(window.sessionStorage) {
					var storedSearchFilter = [];
					var alreadySearchFilter = JSON.parse(sessionStorage.getItem("storedSearchFilter"));
					if (alreadySearchFilter && alreadySearchFilter.length > 0) {
						storedSearchFilter = alreadySearchFilter;

						for(var i= 0; i < storedSearchFilter.length; i++) {
							var currentFilter = storedSearchFilter[i];
							that.forEach(function (type) {
								if (type.data === currentFilter) type.selected = true;
							});
						}
					} else {
						that.forEach(function (type) {
							storedSearchFilter.push(type.data);
							type.selected = true;
						});
						sessionStorage.setItem("storedSearchFilter", JSON.stringify(storedSearchFilter));
					}
				} else {
					that.forEach(function (type) {
						type.selected = true;
					});
				}

				if(typeof cb === 'function'){
					cb();
				}
			}.bind(this));
		},
		noFilter: false
	});
};
