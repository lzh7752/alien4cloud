// components list: browse and search for components
define(function (require) {
  'use strict';

  var modules = require('modules');
  var states = require('states');

  require('scripts/common/services/resize_services');

  require('scripts/_ref/catalog/directives/components_catalog');
  require('scripts/_ref/catalog/controllers/components/components_details');

  states.state('catalog.components.list', {
    url: '/list',
    templateUrl: 'views/_ref/catalog/components/components_list.html',
    controller: 'ComponentsListCtrl',
    resolve: {
      defaultFilters: [function () {
        return {};
      }],
      // badges to display. objet with the following properties:
      //   name: the name of the badge
      //   tooltip: the message to display on the tooltip
      //   imgSrc: the image to display
      //   canDislay: a funtion to decide if the badge is displayabe for a component. takes as param the component and must return true or false.
      //   onClick: callback for the click on the displayed badge. takes as param: the component, the $state object
      badges: [function () {
        return [];
      }]
    },
  });

  modules.get('a4c-catalog', ['ui.router', 'a4c-auth', 'a4c-common']).controller('ComponentsListCtrl', ['$scope', '$state', 'resizeServices', 'defaultFilters', 'badges',
    function ($scope, $state, resizeServices, defaultFilters, badges) {

      $scope.defaultFilters = defaultFilters;
      $scope.badges = badges;

      $scope.openComponent = function (component) {
        $state.go('catalog.components.detail', {id: component.id});
      };

      function onResize(width, height) {
        $scope.heightInfo = {height: height};
        $scope.widthInfo = {width: width};
        $scope.$digest();
      }

      resizeServices.register(onResize, 0, 0);
    }
  ]);
});
