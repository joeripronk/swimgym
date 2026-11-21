/**
 * Global V3 Authentication Initialization for Monolith
 * 
 * This script automatically initializes VgJsAuthHandler (v0.2.6) with V3 authentication
 * for all pages in the monolith. It runs once on page load and makes authenticated
 * API calls available to all JavaScript code.
 * 
 * Usage in your JavaScript:
 * 
 * // Make authenticated GET request
 * VgJsAuthHandler.RequestWrapperPrivateV3.get('/myservice/myendpoint', {
 *   queryParams: {
 *     search: 'value'
 *   },
 *   urlParams: {
 *     id: 123
 *   }
 * }).then(response => {
 *   console.log('Success:', response.data);
 * }).catch(error => {
 *   console.error('Error:', error);
 * });
 * 
 * // Make authenticated POST request
 * VgJsAuthHandler.RequestWrapperPrivateV3.post('/myservice/myendpoint/:id', {
 *   headers: {
 *     'Custom-Header': 'value'
 *   },
 *   urlParams: {
 *     id: 123
 *   },
 *   queryParams: {
 *     filter: 'active'
 *   },
 *   payload: {
 *     key: 'value',
 *     data: 'to send'
 *   }
 * }).then(response => {
 *   console.log('Success:', response.data);
 * }).catch(error => {
 *   console.error('Error:', error);
 * });
 * 
 * Ticket: IN-3912
 */

(function() {
  'use strict';

  // Check if VgJsAuthHandler library is loaded
  if (typeof VgJsAuthHandler === 'undefined' || typeof VgJsAuthHandler.VgAuth === 'undefined') {
    console.warn('VgJsAuthHandler library not loaded. V3 authentication will not be initialized.');
    return;
  }

  // Check if configuration is available
  if (typeof window.vg_auth_config === 'undefined') {
    console.warn('VgJsAuthHandler configuration not found (window.vg_auth_config). V3 authentication will not be initialized.');
    return;
  }

  var config = window.vg_auth_config;

  // Validate required configuration fields
  if (!config.clubId || !config.currentUserId || !config.environment) {
    console.error('VgJsAuthHandler configuration is incomplete. Required: clubId, currentUserId, environment');
    return;
  }

  try {
    // Initialize VgJsAuthHandler with V3 authentication
    VgJsAuthHandler.VgAuth.init({
      clubId: config.clubId,
      currentMemberId: config.currentMemberId,
      currentUserId: config.currentUserId,
      environment: config.environment,
      legacyV2Auth: false, // Use V3 authentication
      logoutUrl: config.logoutUrl,
    });

    console.log('VgJsAuthHandler V3 authentication initialized successfully');
  } catch (error) {
    console.error('Failed to initialize VgJsAuthHandler:', error);
  }
})();


