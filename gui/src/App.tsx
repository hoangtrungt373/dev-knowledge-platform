import { Navigate, Route, Routes } from 'react-router-dom';
import NavBar from './components/NavBar';
import { NotificationProvider } from './contexts/NotificationContext';
import './App.css';
import Login from './pages/Login';
import SignUp from './pages/SignUp';
import Dashboard from './pages/Dashboard';
import AuthCallback from './pages/AuthCallback';
import AdminLogin from './pages/AdminLogin';
import AdminDashboard from './pages/AdminDashboard';
import { authService } from './services';

function App() {
  return (
    <NotificationProvider>
      <div className="App">
        <NavBar />
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<SignUp />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route path="/admin/login" element={<AdminLogin />} />
          <Route path="/admin/dashboard" element={<AdminDashboard />} />
          <Route
            path="/"
            element={
              <Navigate
                to={authService.isAuthenticated() ? '/dashboard' : '/login'}
                replace
              />
            }
          />
        </Routes>
      </div>
    </NotificationProvider>
  );
}

export default App;
