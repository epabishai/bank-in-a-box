import HomePage from "./components/HomePage.jsx";
import AccountDashboard from "./components/AccountDashboard.jsx";

export default function App() {
  const accountRoute = window.location.pathname.match(/^\/accounts\/([^/]+)\/?$/);
  if (accountRoute) {
    return <AccountDashboard accountId={decodeURIComponent(accountRoute[1])} />;
  }

  return <HomePage />;
}
