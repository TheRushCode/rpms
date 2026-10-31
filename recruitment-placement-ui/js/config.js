/**
 * Kiran Academy Recruitment & Placement Management System (RPMS)
 * Frontend Environment Configuration
 *
 * This file configures the backend API base URL for the RPMS frontend.
 *
 * 1. LOCAL DEVELOPMENT:
 *    When accessed via localhost, 127.0.0.1, or file://, it defaults to:
 *    http://localhost:8080/api
 *
 * 2. PRODUCTION / RENDER DEPLOYMENT:
 *    - Update defaultProductionApiUrl below with your deployed Render backend service URL.
 *      (e.g., 'https://rpms-backend.onrender.com/api')
 *    - Or run in the browser console on your deployed site:
 *      setRpmsApiUrl('https://rpms-backend.onrender.com/api')
 */

(function () {
  const defaultLocalApiUrl = 'http://localhost:8080/api';
  const defaultProductionApiUrl = 'https://rpms-backend.onrender.com/api';

  const isLocalHost =
    !window.location.hostname ||
    window.location.hostname === 'localhost' ||
    window.location.hostname === '127.0.0.1' ||
    window.location.protocol === 'file:';

  const storedUrl = typeof localStorage !== 'undefined' ? localStorage.getItem('RPMS_API_BASE_URL') : null;

  window.RPMS_API_BASE_URL =
    window.RPMS_API_BASE_URL ||
    storedUrl ||
    (isLocalHost ? defaultLocalApiUrl : defaultProductionApiUrl);

  // Convenience helper to dynamically set or reset the API URL without re-deploying
  window.setRpmsApiUrl = function (newUrl) {
    if (!newUrl) {
      localStorage.removeItem('RPMS_API_BASE_URL');
      console.log('RPMS_API_BASE_URL cleared. Defaulting based on host.');
    } else {
      localStorage.setItem('RPMS_API_BASE_URL', newUrl.trim());
      console.log('RPMS_API_BASE_URL updated to: ' + newUrl.trim());
    }
    location.reload();
  };
})();
