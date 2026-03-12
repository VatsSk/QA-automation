import * as api from './api.js';
import { initTheme, toggleTheme } from './utils.js';

initTheme();
document.getElementById('theme-btn').addEventListener('click', toggleTheme);

// If already logged in, go straight to dashboard
if (localStorage.getItem('qa_token')) {
    window.location.href = 'index.html';
}

const form    = document.getElementById('login-form');
const btn     = document.getElementById('login-btn');
const errMsg  = document.getElementById('error-msg');

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    errMsg.style.display = 'none';

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;

    if (!username || !password) {
        showError('Please enter username and password.');
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Signing in…';

    try {
        const res = await api.auth.login(username, password);
        localStorage.setItem('qa_token', res.token);
        localStorage.setItem('qa_user', JSON.stringify({
            userId: res.userId,
            username: res.username,
            role: res.role,
        }));
        window.location.href = 'index.html';
    } catch (err) {
        showError(err.message || 'Login failed. Check your credentials.');
        btn.disabled = false;
        btn.textContent = 'Sign In';
    }
});

function showError(msg) {
    errMsg.textContent = msg;
    errMsg.style.display = 'block';
}