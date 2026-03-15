import {Routes} from 'react-router-dom';
import NavBar from './components/NavBar';
import {NotificationProvider} from './contexts/NotificationContext';
import './App.css';

function App() {
  return (
      <NotificationProvider>
        <div className="App">
          <NavBar/>
          <Routes>
          </Routes>
        </div>
      </NotificationProvider>
  );
}

export default App;
