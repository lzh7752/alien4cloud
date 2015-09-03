define(function(require) {
  'use strict';

  var modules = require('modules');
  var states = require('states');
  var angular = require('angular');
  var _ = require('lodash');

  require('scripts/orchestrators/controllers/orchestrator_resource_template');
  require('scripts/orchestrators/directives/orchestrator_resource_template');

  states.state('admin.orchestrators.details.locations.infra', {
    url: '/infra',
    templateUrl: 'views/orchestrators/orchestrator_locations_infra.html',
    controller: 'OrchestratorLocationsConfigCtrl',
    menu: {
      id: 'menu.orchestrators.locations.infra',
      state: 'admin.orchestrators.details.locations.infra',
      key: 'ORCHESTRATORS.LOCATIONS.CONFIGURATION_RESOURCES',
      icon: 'fa fa-wrench',
      priority: 100
    }
  });

  modules.get('a4c-orchestrators', ['ui.router', 'ui.bootstrap', 'a4c-common']).controller('OrchestratorLocationsConfigCtrl',
    ['$scope', 'orchestrator', 'locationResourcesService',
      function($scope, orchestrator, locationResourcesService) {
        $scope.orchestrator = orchestrator;
        if (_.isNotEmpty($scope.context.configurationTypes)) {
          $scope.selectedConfigurationResourceType = $scope.context.configurationTypes[0];
        }
        $scope.addResourceTemplate = function() {
          locationResourcesService.save({
            orchestratorId: $scope.orchestrator.id,
            locationId: $scope.context.location.id
          }, angular.toJson({
            'resourceType': $scope.selectedConfigurationResourceType.elementId,
            'resourceName': 'New Resource'
          }), function(response) {
            $scope.context.locationResources.configurationTemplates.push(response.data);
          })
        };

        $scope.selectTemplate = function(template) {
          $scope.selectedConfigurationResourceTemplate = template;
        };

        $scope.saveResourceTemplate = function(resourceTemplate) {
          console.log('Update', resourceTemplate);
        };

        $scope.deleteResourceTemplate = function(resourceTemplate) {
          console.log('Delete', resourceTemplate);
        };
      }
    ]); // controller
}); // define
