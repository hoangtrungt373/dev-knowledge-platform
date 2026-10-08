import { useSearchParams } from 'react-router-dom';
import { FormTab, isFormTab } from '../utils/problemForm';

/**
 * The problem form's open tab, kept in the URL (`?tab=signature`) so a reload or a shared link
 * reopens it. Written with `replace`, so switching tabs doesn't pile up browser history; the default
 * tab (`details`) is written as no param at all.
 */
export function useFormTab(): [FormTab, (tab: FormTab) => void] {
  const [searchParams, setSearchParams] = useSearchParams();
  const param = searchParams.get('tab');
  const activeTab: FormTab = isFormTab(param) ? param : 'details';

  const setActiveTab = (tab: FormTab) =>
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      if (tab === 'details') next.delete('tab');
      else next.set('tab', tab);
      return next;
    }, { replace: true });

  return [activeTab, setActiveTab];
}
