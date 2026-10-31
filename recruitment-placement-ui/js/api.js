/**
 * Kiran Academy Recruitment & Placement Management System (RPMS)
 * Central API Client & UI Utilities
 */

const API_BASE_URL = (typeof window !== 'undefined' && window.RPMS_API_BASE_URL)
  ? window.RPMS_API_BASE_URL
  : (typeof localStorage !== 'undefined' && localStorage.getItem('RPMS_API_BASE_URL'))
    ? localStorage.getItem('RPMS_API_BASE_URL')
    : 'http://localhost:8080/api';

/**
 * Escapes HTML characters to prevent XSS injection in dynamic rendering.
 */
function escapeHtml(text) {
  if (text === null || text === undefined) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/**
 * Displays a non-intrusive toast notification.
 */
function showNotification(message, type = 'info') {
  let toastContainer = document.getElementById('rpms-toast-container');
  if (!toastContainer) {
    toastContainer = document.createElement('div');
    toastContainer.id = 'rpms-toast-container';
    toastContainer.style.position = 'fixed';
    toastContainer.style.bottom = '20px';
    toastContainer.style.right = '20px';
    toastContainer.style.zIndex = '99999';
    toastContainer.style.display = 'flex';
    toastContainer.style.flexDirection = 'column';
    toastContainer.style.gap = '10px';
    document.body.appendChild(toastContainer);
  }

  const toast = document.createElement('div');
  const bgMap = {
    success: '#10b981',
    error: '#ef4444',
    warning: '#f59e0b',
    info: '#2563eb'
  };

  toast.style.background = bgMap[type] || bgMap.info;
  toast.style.color = '#ffffff';
  toast.style.padding = '12px 18px';
  toast.style.borderRadius = '6px';
  toast.style.fontSize = '14px';
  toast.style.fontWeight = '500';
  toast.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
  toast.style.transition = 'all 0.3s ease';
  toast.style.maxWidth = '380px';
  toast.style.wordBreak = 'break-word';
  toast.textContent = message;

  toastContainer.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(10px)';
    setTimeout(() => {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 300);
  }, 4000);
}

/**
 * Common fetch wrapper with credentials, resilient token headers, and error handling.
 */
async function apiRequest(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  const defaultHeaders = {
    'Accept': 'application/json'
  };

  // Attach active session token if present
  const token = typeof localStorage !== 'undefined' ? localStorage.getItem('rpmsAuthToken') : null;
  if (token) {
    defaultHeaders['X-Auth-Token'] = token;
    defaultHeaders['Authorization'] = `Bearer ${token}`;
  }

  if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
    defaultHeaders['Content-Type'] = 'application/json';
    options.body = JSON.stringify(options.body);
  }

  options.headers = {
    ...defaultHeaders,
    ...options.headers
  };

  // Always include credentials for cross-origin session cookies
  options.credentials = 'include';

  try {
    const response = await fetch(url, options);

    const contentType = response.headers.get('content-type');
    const isJson = contentType && contentType.includes('application/json');
    const data = isJson ? await response.json() : await response.text();

    if (!response.ok) {
      // Handle 401 Unauthorized redirect
      if (response.status === 401 && typeof window !== 'undefined') {
        const isAuthPage = window.location.pathname.endsWith('login.html') ||
                           window.location.pathname.endsWith('register.html') ||
                           window.location.pathname === '/' ||
                           window.location.pathname.endsWith('/');
        if (!isAuthPage) {
          Api.setUser(null);
          localStorage.removeItem('rpmsAuthToken');
          window.location.href = 'login.html';
        }
      }

      let errMsg = 'An unexpected error occurred.';
      if (data && typeof data === 'object') {
        errMsg = data.message || data.error || JSON.stringify(data);
        if (data.validationErrors) {
          const details = Object.entries(data.validationErrors).map(([f, m]) => `${f}: ${m}`).join(', ');
          errMsg += ` (${details})`;
        }
      } else if (typeof data === 'string' && data.length > 0) {
        errMsg = data;
      }
      throw new Error(errMsg);
    }

    return data;
  } catch (error) {
    console.error(`API Error on [${options.method || 'GET'}] ${endpoint}:`, error);
    throw error;
  }
}

// API methods
const Api = {
  // Master Data
  getSkills: () => apiRequest('/skills'),
  getQualifications: () => apiRequest('/qualifications'),
  getRoles: () => apiRequest('/roles'),

  // Candidates
  getCandidates: (params = {}) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && String(v).trim() !== '') {
        qs.append(k, v);
      }
    });
    const qStr = qs.toString();
    return apiRequest(`/candidates${qStr ? '?' + qStr : ''}`);
  },
  getCandidateById: (id) => apiRequest(`/candidates/${id}`),
  createCandidate: (candidateData) => apiRequest('/candidates', {
    method: 'POST',
    body: candidateData
  }),
  updateCandidate: (id, candidateData) => apiRequest(`/candidates/${id}`, {
    method: 'PUT',
    body: candidateData
  }),
  getCandidateApplications: (id) => apiRequest(`/candidates/${id}/applications`),

  // Companies
  getCompanies: (params = {}) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && String(v).trim() !== '') {
        qs.append(k, v);
      }
    });
    const qStr = qs.toString();
    return apiRequest(`/companies${qStr ? '?' + qStr : ''}`);
  },
  getCompanyById: (id) => apiRequest(`/companies/${id}`),
  createCompany: (companyData) => apiRequest('/companies', {
    method: 'POST',
    body: companyData
  }),
  updateCompany: (id, companyData) => apiRequest(`/companies/${id}`, {
    method: 'PUT',
    body: companyData
  }),

  // Jobs
  getJobs: (params = {}) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && String(v).trim() !== '') {
        qs.append(k, v);
      }
    });
    const qStr = qs.toString();
    return apiRequest(`/jobs${qStr ? '?' + qStr : ''}`);
  },
  getJobById: (id) => apiRequest(`/jobs/${id}`),
  createJob: (jobData) => apiRequest('/jobs', {
    method: 'POST',
    body: jobData
  }),
  updateJob: (id, jobData) => apiRequest(`/jobs/${id}`, {
    method: 'PUT',
    body: jobData
  }),

  // Applications
  getApplications: (params = {}) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && String(v).trim() !== '') {
        qs.append(k, v);
      }
    });
    const qStr = qs.toString();
    return apiRequest(`/applications${qStr ? '?' + qStr : ''}`);
  },
  getApplicationById: (id) => apiRequest(`/applications/${id}`),
  createApplication: (appData) => apiRequest('/applications', {
    method: 'POST',
    body: appData
  }),
  updateApplicationStatus: (id, statusData) => apiRequest(`/applications/${id}/status`, {
    method: 'PUT',
    body: statusData
  }),

  // Interviews
  getInterviews: (params = {}) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && String(v).trim() !== '') {
        qs.append(k, v);
      }
    });
    const qStr = qs.toString();
    return apiRequest(`/interviews${qStr ? '?' + qStr : ''}`);
  },
  getInterviewById: (roundId) => apiRequest(`/interviews/${roundId}`),
  scheduleInterview: (roundData) => apiRequest('/interviews/schedule', {
    method: 'POST',
    body: roundData
  }),
  submitFeedback: (roundId, feedbackData) => apiRequest(`/interviews/${roundId}/feedback`, {
    method: 'POST',
    body: feedbackData
  }),

  // Offers & Placements
  getOffers: (params = {}) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && String(v).trim() !== '') {
        qs.append(k, v);
      }
    });
    const qStr = qs.toString();
    return apiRequest(`/offers${qStr ? '?' + qStr : ''}`);
  },
  createOffer: (offerData) => apiRequest('/offers', {
    method: 'POST',
    body: offerData
  }),
  updateOfferStatus: (id, statusData) => apiRequest(`/offers/${id}/status`, {
    method: 'PUT',
    body: statusData
  }),
  getPlacements: (params = {}) => {
    const qs = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && String(v).trim() !== '') {
        qs.append(k, v);
      }
    });
    const qStr = qs.toString();
    return apiRequest(`/placements${qStr ? '?' + qStr : ''}`);
  },
  createPlacement: (placementData) => apiRequest('/placements', {
    method: 'POST',
    body: placementData
  }),

  // Dashboard & Reports
  getDashboardStats: () => apiRequest('/dashboard/stats'),
  getDashboardFunnel: () => apiRequest('/dashboard/funnel'),
  getReportsFunnel: () => apiRequest('/reports/funnel'),
  getCompanyWiseReport: () => apiRequest('/reports/company-wise'),
  getTechWiseReport: () => apiRequest('/reports/tech-wise'),

  // Authentication
  register: (userData) => apiRequest('/auth/register', {
    method: 'POST',
    body: userData
  }),
  login: async (credentials) => {
    const res = await apiRequest('/auth/login', {
      method: 'POST',
      body: credentials
    });
    if (res && res.user) {
      Api.setUser(res.user);
    }
    if (res && res.token) {
      localStorage.setItem('rpmsAuthToken', res.token);
    }
    return res;
  },
  getCurrentUser: () => apiRequest('/auth/me'),
  logout: async () => {
    try {
      await apiRequest('/auth/logout', { method: 'POST' });
    } catch (e) {
      // Continue clean local sign out even if network fails
    }
    Api.setUser(null);
    localStorage.removeItem('rpmsAuthToken');
    window.location.href = 'login.html';
  },
  forgotPassword: (email) => apiRequest('/auth/forgot-password', {
    method: 'POST',
    body: { email }
  }),
  resetPassword: (payload) => apiRequest('/auth/reset-password', {
    method: 'POST',
    body: payload
  }),

  // UI Utilities & Formatters
  escapeHtml: escapeHtml,
  showAlert: showNotification,
  showNotification: showNotification,

  formatDate: (dateVal) => {
    if (!dateVal) return 'N/A';
    try {
      const d = new Date(dateVal);
      if (isNaN(d.getTime())) return String(dateVal);
      return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    } catch {
      return String(dateVal);
    }
  },

  formatDateTime: (dateVal) => {
    if (!dateVal) return 'N/A';
    try {
      const d = new Date(dateVal);
      if (isNaN(d.getTime())) return String(dateVal);
      return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) + ' ' +
             d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true });
    } catch {
      return String(dateVal);
    }
  },

  getUser: () => {
    try {
      const u = localStorage.getItem('rpmsUser') || localStorage.getItem('rpms_user');
      return u ? JSON.parse(u) : null;
    } catch {
      return null;
    }
  },

  setUser: (user) => {
    try {
      if (user) {
        localStorage.setItem('rpmsUser', JSON.stringify(user));
        localStorage.setItem('rpms_user', JSON.stringify(user));
      } else {
        localStorage.removeItem('rpmsUser');
        localStorage.removeItem('rpms_user');
      }
    } catch (e) {
      console.warn('Could not save user session to localStorage', e);
    }
  },

  updateNavUser: () => {
    const user = Api.getUser();
    if (!user) return;
    const nameEl = document.querySelector('.user-name');
    const roleEl = document.querySelector('.user-role');
    const avatarEl = document.querySelector('.user-avatar-circle');

    const name = user.fullName || user.full_name || user.username || 'User';
    const role = user.roleName || user.role_name || user.roleCode || user.role_code || 'Recruiter';

    if (nameEl) nameEl.textContent = name;
    if (roleEl) roleEl.textContent = role;
    if (avatarEl) {
      const parts = name.trim().split(/\s+/);
      const initials = (parts[0][0] + (parts.length > 1 ? parts[1][0] : '')).toUpperCase();
      avatarEl.textContent = initials || 'U';
    }
  },

  requireAuth: () => {
    const user = Api.getUser();
    if (!user) {
      window.location.href = 'login.html';
      return false;
    }
    return true;
  }
};

// Auto-wire logout buttons, nav user, and route guards
if (typeof window !== 'undefined' && typeof document !== 'undefined') {
  const currentPath = window.location.pathname;
  const isAuthPage = currentPath.endsWith('login.html') ||
                     currentPath.endsWith('register.html') ||
                     currentPath === '/' ||
                     currentPath.endsWith('/');

  if (!isAuthPage && !Api.getUser()) {
    window.location.href = 'login.html';
  }

  document.addEventListener('DOMContentLoaded', () => {
    Api.updateNavUser();
    document.querySelectorAll('.header-logout-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        Api.logout();
      });
    });
  });
}

window.escapeHtml = escapeHtml;
window.showNotification = showNotification;
window.Api = Api;
