angular.module('im', [
'ui.bootstrap',
'ngRoute'
]);

angular.module('im').config([ '$routeProvider', function($routeProvider) {
	
	$routeProvider.when('/register', {
		templateUrl : 'templates/registerInstance.html',
		controller : 'registerInstanceCtrl'
	})
	.otherwise({
		redirectTo : '/register'
	});

}]);