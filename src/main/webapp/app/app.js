angular.module('rid', [
'ui.bootstrap',
'ngRoute'
]);

angular.module('rid').config([ '$routeProvider', function($routeProvider) {
	
	$routeProvider.when('/register', {
		templateUrl : 'templates/registerInstance.html',
		controller : 'registerInstanceCtrl'
	})
	.otherwise({
		redirectTo : '/register'
	});

}]);