angular.module('rid').controller('registerInstanceCtrl', function ($scope,$http) {
	$scope.inst = {};
	
	$scope.doRegister = function(inst) {
		//console.log("register instance with prefix " + prefix);
		$http({
			method: 'POST',
			//url: 'https://identity.restcomm.com/instance-manager/api/instances',
			url: 'https://192.168.1.39:8443/restcomm-identity/api/instances',
			data: $.param(inst),
			headers: {'Content-Type': 'application/x-www-form-urlencoded'}
		});
	}
});