
/**
 * search engine routes declaration
 */
routes.define(function($routeProvider){
	$routeProvider
			.when('/:searchtext', {
				action: 'launchSearch'
			});
});


function SearchEngine($rootScope, $scope, template, date, lang, model, route){
	/**
	 * search engine routes definition
	 */
	route({
		launchSearch: function(params){
			console.log("route lauchSearch " + params.searchtext);
			$scope.currentErrors = [];
			$scope.hasMoreResult = [];
			model.searchField.init();
			$scope.searchField.words =  (params.searchtext === ' ') ? '' : params.searchtext;
			model.searchs.clear();
			model.searchTypes.sync(function() {
				model.searchs.sync(false,
					function(status, hasMoreResult){
						searchOk(status, hasMoreResult);
					},
					function(e) {
						searchError(e);
					}
				);
			});
		}
	});

	template.open('main', 'main');
	template.open('errors', 'errors');
	template.open('hasMoreResult', 'has-more-result');
	$scope.template = template;
	$scope.lang = lang;

	$scope.currentErrors = [];
	$scope.hasMoreResult = [];
	$scope.searchTypes = model.searchTypes;
	$scope.translate = lang.translate;
	$scope.searchs = model.searchs;
	$scope.searchField = model.searchField;

	$scope.formatDate = function(dateString){
		return date.calendar(dateString);
	};

	$scope.selectFilter = function(filter){
		filter.apply();
		$scope.currentErrors = [];
		$scope.hasMoreResult = [];
		if(model.searchTypes.noFilter){
			model.searchTypes.deselectAll();
		}
		model.searchs.clear();
		model.searchs.sync(false,
			function(status, hasMoreResult){
				searchOk(status, hasMoreResult);
			},
			function(e) {
				searchError(e);
			}
		);
	};

	$scope.loadPage = function(){
		$scope.currentErrors = [];
		$scope.hasMoreResult = [];
		model.searchs.sync(true,
			function(status, hasMoreResult){
				searchOk(status, hasMoreResult);
			},
			function(e) {
				searchError(e);
			}
		);
	};

	$scope.launchSearching = function(mysearch, event) {
		event.stopPropagation();
		console.log("lauchSearch method" + mysearch);
		$scope.searching();
	};

	$scope.searching = function() {
		model.searchField.init();
		$scope.currentErrors = [];
		$scope.hasMoreResult = [];
		model.searchs.clear();
		model.searchs.sync(false,
				function(status, hasMoreResult){
					searchOk(status, hasMoreResult);
				},
				function(e) {
					searchError(e);
				}
		);
	};


	var searchOk = function(status, hasMoreResult){
		if (status) {
			$scope.currentErrors.push({error: 'search.engine.result.partial'});
			notify.info('search.engine.result.partial');
		}
		$scope.hasMoreResult.push((hasMoreResult) ?
			{message: 'search.engine.has.more'} :
			{message: 'search.engine.has.nomore'});
		$scope.$apply();
	};

	var searchError = function(e){
		notify.error(e.error);
		$scope.currentErrors.push(e);
		$scope.$apply();
	};
}