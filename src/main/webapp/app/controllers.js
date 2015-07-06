angular.module('im').controller('registerInstanceCtrl', function ($scope) {
	console.log("this is registerInstanceCtrl");
	
	$scope.doRegister = function(prefix) {
		console.log("register instance with prefix " + prefix);
	}
});